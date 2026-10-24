package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Controlli sui file in ingresso: falliscono prima di qualsiasi chiamata all'AI (nessuna chiave necessaria). */
class ReceiptScanServiceTest {

	private final ReceiptScanService service = new ReceiptScanService("claude-opus-5-5");

	private int statusOf(List<MockMultipartFile> files) {
		return assertThrows(ResponseStatusException.class, () -> service.scan(List.copyOf(files))).getStatusCode()
				.value();
	}

	@Test
	void rejectsEmptyUnsupportedAndOversizedInput() {
		assertEquals(400, statusOf(List.of()));
		assertEquals(415, statusOf(List.of(new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes()))));
		assertEquals(413, statusOf(List.of(new MockMultipartFile("file", "big.jpg", "image/jpeg",
				new byte[(int) ReceiptScanService.MAX_IMAGE_BYTES + 1]))));
		MockMultipartFile ok = new MockMultipartFile("file", "a.jpg", "image/jpeg", "x".getBytes());
		assertEquals(400, statusOf(List.of(ok, ok, ok, ok, ok)));
	}

	private static it.glknet.financial_manager.dto.ReceiptItemDto item(String name, String qty, String unit, String total,
			String discount) {
		return new it.glknet.financial_manager.dto.ReceiptItemDto(name, name, "Bevande", new java.math.BigDecimal(qty),
				"pz", new java.math.BigDecimal(unit), new java.math.BigDecimal(total),
				discount == null ? null : new java.math.BigDecimal(discount));
	}

	@Test
	void discountsEndUpOnTheirItem() {
		var out = ReceiptScanService.attachDiscounts(List.of(
				item("Birra", "15", "1.28", "19.20", "0"), // l'AI ha messo lo sconto su una riga a parte
				item("Sconto: Birra", "15", "-0.43", "-6.45", "0"),
				item("Latte", "10", "1.59", "15.90", "7.00"), // sconto indicato ma totale ancora pieno
				item("Pane", "1", "1.76", "1.76", "0"),
				item("Sconto: buono fedelta'", "1", "-5.00", "-5.00", "0"))); // sconto sul totale: resta

		assertEquals(List.of("Birra", "Latte", "Pane", "Sconto: buono fedelta'"),
				out.stream().map(i -> i.name()).toList());
		assertEquals(0, new java.math.BigDecimal("6.45").compareTo(out.get(0).discount()));
		assertEquals(0, new java.math.BigDecimal("12.75").compareTo(out.get(0).totalPrice()));
		assertEquals(0, new java.math.BigDecimal("8.90").compareTo(out.get(1).totalPrice()));
		assertEquals(0, new java.math.BigDecimal("1.76").compareTo(out.get(2).totalPrice()));
		assertEquals(0, new java.math.BigDecimal("-5.00").compareTo(out.get(3).totalPrice()));
	}
}
