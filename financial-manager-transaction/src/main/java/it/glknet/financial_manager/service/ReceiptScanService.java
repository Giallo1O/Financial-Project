package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;

import it.glknet.financial_manager.dto.ReceiptItemDto;
import it.glknet.financial_manager.dto.ScannedReceipt;
import lombok.extern.slf4j.Slf4j;

/** Legge foto/PDF di scontrini con Claude e restituisce una bozza strutturata (non salva nulla). */
@Service
@Slf4j
public class ReceiptScanService {

	static final int MAX_FILES = 4;
	static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024; // limite dell'API per immagine
	static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

	static final String SYSTEM_PROMPT = """
			Leggi scontrini della spesa italiani (foto o PDF) ed estrai i dati in modo fedele.
			Se ricevi piu' immagini sono parti consecutive dello stesso scontrino: restituisci un unico scontrino.

			Regole:
			- storeName: solo l'insegna, senza indirizzo o ragione sociale.
			- total: il totale effettivamente pagato (dopo gli sconti).
			- Una voce in items per ogni articolo acquistato, nell'ordine dello scontrino. Non includere subtotali, IVA, resto, metodo di pagamento, punti fedelta'.
			- name: nome leggibile in italiano; espandi le abbreviazioni (es. "LATTE P.S. 1L" diventa "Latte parzialmente scremato 1L"). Lo stesso prodotto deve avere sempre lo stesso nome, quindi non aggiungere dettagli che non sono stampati.
			- Articoli a peso o volume (es. "0,532 kg x 2,99 EUR/kg"): quantity e' il peso, unit e' kg, unitPrice e' il prezzo al kg. Per gli altri articoli unit e' pz e quantity il numero di pezzi.
			- Se una riga riporta quantita' e prezzo unitario (es. "2 x 1,20"), unitPrice e' 1.20 (prezzo pieno).
			- Sconti, promozioni e "sconto %" stampati sotto un articolo vanno sull'articolo stesso, non in una voce a parte:
			  discount e' l'importo totale scontato su quella riga (positivo) e totalPrice e' quanto pagato dopo lo sconto.
			  Esempio: "2 x 1,20 = 2,40" seguito da "SCONTO -0,80" diventa quantity 2, unitPrice 1.20, discount 0.80, totalPrice 1.60.
			  Senza sconto discount e' 0 e totalPrice e' quantity x unitPrice.
			- Solo gli sconti sul totale non legati a un articolo (buoni, punti fedelta' spesi, arrotondamenti) restano una voce a parte
			  con name "Sconto: <descrizione>", foodCategory Altro, quantity 1, unitPrice e totalPrice negativi, discount 0.
			- La somma dei totalPrice deve essere uguale a total.
			- Numeri con punto decimale (es. 1.20). Non inventare: se una riga e' illeggibile omettila e segnalala in warnings.
			""";

	private final String model;
	private volatile AnthropicClient client;

	public ReceiptScanService(@Value("${anthropic.model:claude-opus-5-5}") String model) {
		this.model = model;
	}

	private AnthropicClient client() {
		if (client == null) {
			synchronized (this) {
				if (client == null) {
					// Legge ANTHROPIC_API_KEY (e ANTHROPIC_BASE_URL) dall'ambiente; creato al primo uso cosi' l'app parte anche senza chiave
					client = AnthropicOkHttpClient.fromEnv();
				}
			}
		}
		return client;
	}

	public ScannedReceipt scan(List<MultipartFile> files) {
		if (files == null || files.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nessun file caricato");
		}
		if (files.size() > MAX_FILES) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Al massimo " + MAX_FILES + " file per scontrino");
		}

		List<ContentBlockParam> content = new ArrayList<>();
		for (MultipartFile file : files) {
			content.add(toBlock(file));
		}
		content.add(ContentBlockParam.ofText(TextBlockParam.builder().text("Estrai i dati di questo scontrino.").build()));

		StructuredMessageCreateParams<ScannedReceipt> params = MessageCreateParams.builder().model(model)
				.maxTokens(16000L).system(SYSTEM_PROMPT).outputConfig(ScannedReceipt.class)
				.addUserMessageOfBlockParams(content).build();

		try {
			StructuredMessage<ScannedReceipt> response = client().messages().create(params);
			ScannedReceipt result = response.content().stream().flatMap(block -> block.text().stream()).findFirst()
					.map(typed -> typed.text()).orElse(null);
			if (result == null || result.items() == null) {
				log.warn("Risposta senza scontrino leggibile, stop_reason={}", response.stopReason());
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
						"Non sono riuscito a leggere lo scontrino. Riprova con una foto piu' nitida.");
			}
			return new ScannedReceipt(result.storeName(), result.purchaseDate(), result.total(),
					attachDiscounts(result.items()), result.warnings());
		} catch (AnthropicServiceException e) {
			log.error("Errore API Anthropic {}: {}", e.statusCode(), e.getMessage());
			if (e.statusCode() == 401 || e.statusCode() == 403) {
				throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
						"Chiave API Anthropic non valida: controlla ANTHROPIC_API_KEY sul server");
			}
			if (e.statusCode() == 429) {
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Troppe richieste, riprova tra poco");
			}
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Errore del servizio di lettura scontrini");
		} catch (AnthropicException e) {
			log.error("Impossibile chiamare l'API Anthropic", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Servizio di lettura scontrini non raggiungibile o ANTHROPIC_API_KEY mancante sul server");
		}
	}

	private ContentBlockParam toBlock(MultipartFile file) {
		String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (java.io.IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File non leggibile");
		}
		if (bytes.length == 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File vuoto");
		}
		String data = Base64.getEncoder().encodeToString(bytes);
		if (type.equals("application/pdf")) {
			return ContentBlockParam.ofDocument(
					DocumentBlockParam.builder().source(Base64PdfSource.builder().data(data).build()).build());
		}
		if (!IMAGE_TYPES.contains(type)) {
			throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
					"Formato non supportato: usa JPG, PNG, WebP o PDF");
		}
		if (bytes.length > MAX_IMAGE_BYTES) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "L'immagine supera i 5 MB");
		}
		Base64ImageSource.MediaType mediaType = switch (type) {
			case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
			case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
			case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
			default -> Base64ImageSource.MediaType.IMAGE_JPEG;
		};
		return ContentBlockParam.ofImage(ImageBlockParam.builder()
				.source(Base64ImageSource.builder().data(data).mediaType(mediaType).build()).build());
	}

	/**
	 * Rete di sicurezza sulla risposta dell'AI: una riga "Sconto: X" negativa subito dopo l'articolo X viene unita
	 * all'articolo (discount e totalPrice netto); se un articolo ha uno sconto ma totalPrice e' ancora il prezzo pieno,
	 * lo sconto viene tolto. Le altre righe negative (sconti sul totale) restano come sono.
	 */
	static List<ReceiptItemDto> attachDiscounts(List<ReceiptItemDto> items) {
		List<ReceiptItemDto> out = new ArrayList<>();
		for (ReceiptItemDto item : items) {
			ReceiptItemDto prev = out.isEmpty() ? null : out.get(out.size() - 1);
			if (prev != null && item.totalPrice() != null && item.totalPrice().signum() < 0 && item.name() != null
					&& prev.name() != null && prev.totalPrice() != null && prev.totalPrice().signum() > 0
					&& item.name().replaceFirst("(?i)^sconto\\s*:?\\s*", "").trim().equalsIgnoreCase(prev.name().trim())) {
				BigDecimal extra = item.totalPrice().negate();
				out.set(out.size() - 1, withDiscount(prev, nz(prev.discount()).add(extra), prev.totalPrice().subtract(extra)));
				continue;
			}
			BigDecimal discount = nz(item.discount());
			if (discount.signum() > 0 && item.unitPrice() != null && item.quantity() != null && item.totalPrice() != null) {
				BigDecimal gross = item.unitPrice().multiply(item.quantity());
				if (gross.subtract(item.totalPrice()).abs().compareTo(new BigDecimal("0.01")) <= 0) {
					item = withDiscount(item, discount, item.totalPrice().subtract(discount));
				}
			}
			out.add(item);
		}
		return out;
	}

	private static ReceiptItemDto withDiscount(ReceiptItemDto i, BigDecimal discount, BigDecimal total) {
		return new ReceiptItemDto(i.rawName(), i.name(), i.foodCategory(), i.quantity(), i.unit(), i.unitPrice(), total,
				discount);
	}

	private static BigDecimal nz(BigDecimal v) {
		return v == null ? BigDecimal.ZERO : v;
	}
}
