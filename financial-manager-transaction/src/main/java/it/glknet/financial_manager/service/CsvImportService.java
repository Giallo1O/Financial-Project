package it.glknet.financial_manager.service;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.TransactionType;

/**
 * Legge gli estratti conto CSV. Se l'intestazione ha colonne riconoscibili (es. N26: "Booking Date", "Partner Name",
 * "Amount (EUR)"; banche italiane: "Data", "Descrizione", "Importo" oppure "Entrate"/"Uscite") usa quelle; altrimenti
 * indovina riga per riga (data = cella che sembra una data, importo = cella numerica, descrizione = testo piu' lungo).
 */
@Service
public class CsvImportService {

	private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

	// Nomi di colonna in ordine di preferenza (minuscolo)
	private static final List<String> DATE_COLS = List.of("booking date", "data operazione", "data contabile",
			"data registrazione", "data", "date", "value date", "data valuta");
	private static final List<String> AMOUNT_COLS = List.of("amount (eur)", "importo (eur)", "importo eur", "importo",
			"amount", "ammontare");
	private static final List<String> IN_COLS = List.of("entrate", "accrediti", "avere");
	private static final List<String> OUT_COLS = List.of("uscite", "addebiti", "dare");
	private static final List<String> DESC_COLS = List.of("partner name", "descrizione operazione", "descrizione",
			"description", "causale", "beneficiario", "payee", "merchant", "esercente");
	private static final List<String> REF_COLS = List.of("payment reference", "riferimento", "dettagli");

	public List<TransactionRequest> parseCsv(MultipartFile file) {
		try {
			String text = decode(file.getBytes());
			char separator = detectSeparator(text);
			List<String[]> all;
			try (CSVReader reader = new CSVReaderBuilder(new StringReader(text))
					.withCSVParser(new CSVParserBuilder().withSeparator(separator).build()).build()) {
				all = reader.readAll();
			}
			if (all.isEmpty()) {
				return List.of();
			}
			Columns cols = Columns.of(all.get(0));
			List<String[]> rows = all.subList(1, all.size());
			return cols.usable() ? byHeader(rows, cols) : byGuess(rows);
		} catch (Exception e) {
			log.error("Failed to parse CSV file", e);
			throw new RuntimeException("Failed to parse CSV: " + e.getMessage());
		}
	}

	private record Columns(int date, int amount, int in, int out, int desc, int ref) {

		static Columns of(String[] header) {
			List<String> h = new ArrayList<>();
			for (String c : header) {
				h.add(c == null ? "" : c.replace("﻿", "").replace("\"", "").trim().toLowerCase(Locale.ROOT));
			}
			return new Columns(find(h, DATE_COLS), find(h, AMOUNT_COLS), find(h, IN_COLS), find(h, OUT_COLS),
					find(h, DESC_COLS), find(h, REF_COLS));
		}

		private static int find(List<String> header, List<String> names) {
			for (String name : names) {
				int i = header.indexOf(name);
				if (i >= 0) {
					return i;
				}
			}
			return -1;
		}

		boolean usable() {
			return date >= 0 && (amount >= 0 || in >= 0 || out >= 0);
		}
	}

	private List<TransactionRequest> byHeader(List<String[]> rows, Columns c) {
		List<TransactionRequest> out = new ArrayList<>();
		for (String[] row : rows) {
			LocalDate date = tryParseDate(cell(row, c.date()));
			BigDecimal amount = c.amount() >= 0 ? parseAmount(cell(row, c.amount())) : null;
			if (amount == null && (c.in() >= 0 || c.out() >= 0)) {
				BigDecimal in = parseAmount(cell(row, c.in()));
				BigDecimal outAmount = parseAmount(cell(row, c.out()));
				amount = (in == null ? BigDecimal.ZERO : in.abs())
						.subtract(outAmount == null ? BigDecimal.ZERO : outAmount.abs());
			}
			if (date == null || amount == null || amount.signum() == 0) {
				continue; // righe di saldo, totali o vuote
			}
			String desc = cell(row, c.desc());
			if (desc.isEmpty()) {
				desc = cell(row, c.ref());
			}
			out.add(new TransactionRequest(desc.isEmpty() ? "Movimento importato" : desc, amount.abs(),
					amount.signum() > 0 ? TransactionType.ENTRATA : TransactionType.USCITA, null, null, date));
		}
		return out;
	}

	/** Formato sconosciuto: indovina le colonne riga per riga. */
	private List<TransactionRequest> byGuess(List<String[]> rows) {
		List<TransactionRequest> out = new ArrayList<>();
		for (String[] row : rows) {
			String description = "";
			BigDecimal amount = null;
			LocalDate date = null;
			for (String col : row) {
				String clean = col == null ? "" : col.replace("\"", "").trim();
				if (clean.isEmpty()) {
					continue;
				}
				LocalDate d = date == null ? tryParseDate(clean) : null;
				if (d != null) {
					date = d;
					continue;
				}
				BigDecimal a = amount == null ? parseAmount(clean) : null;
				if (a != null) {
					amount = a;
					continue;
				}
				if (description.length() < clean.length()) {
					description = clean;
				}
			}
			if (amount == null || amount.signum() == 0) {
				continue;
			}
			out.add(new TransactionRequest(description.isEmpty() ? "Movimento importato" : description, amount.abs(),
					amount.signum() > 0 ? TransactionType.ENTRATA : TransactionType.USCITA, null, null,
					date != null ? date : LocalDate.now()));
		}
		return out;
	}

	private static String cell(String[] row, int i) {
		return i < 0 || i >= row.length || row[i] == null ? "" : row[i].replace("\"", "").trim();
	}

	/** "1.234,56", "1,234.56", "-45,50", "€ 12.00" -> BigDecimal; null se non e' un importo. */
	static BigDecimal parseAmount(String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.replace("€", "").replace("EUR", "").replace(" ", "").replace(" ", "").trim();
		if (!s.matches("^[+-]?[\\d.,]*\\d$")) {
			return null;
		}
		int comma = s.lastIndexOf(','), dot = s.lastIndexOf('.');
		if (comma >= 0 && dot >= 0) {
			s = comma > dot ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
		} else if (comma >= 0) {
			s = s.matches("^[+-]?\\d{1,3}(,\\d{3})+$") ? s.replace(",", "") : s.replace(',', '.');
		} else if (s.matches("^[+-]?\\d{1,3}(\\.\\d{3}){2,}$")) {
			s = s.replace(".", ""); // 1.234.567
		}
		try {
			return new BigDecimal(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	static LocalDate tryParseDate(String raw) {
		if (raw == null || !raw.matches("^\\d{1,4}[/.-]\\d{1,2}[/.-]\\d{1,4}([ T].*)?$")) {
			return null;
		}
		String[] p = raw.split("[ T]")[0].split("[/.-]");
		try {
			if (p[0].length() == 4) {
				return LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
			}
			int year = Integer.parseInt(p[2]);
			return LocalDate.of(year < 100 ? 2000 + year : year, Integer.parseInt(p[1]), Integer.parseInt(p[0]));
		} catch (Exception e) {
			return null;
		}
	}

	private static char detectSeparator(String text) {
		String first = text.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
		long semi = first.chars().filter(ch -> ch == ';').count();
		long comma = first.chars().filter(ch -> ch == ',').count();
		long tab = first.chars().filter(ch -> ch == '\t').count();
		if (tab > semi && tab > comma) {
			return '\t';
		}
		return semi > comma ? ';' : ',';
	}

	/** UTF-8 (anche con BOM); se non valido, Windows-1252 come esportano molte banche italiane. */
	private static String decode(byte[] bytes) {
		try {
			return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes)).toString().replace("﻿", "");
		} catch (CharacterCodingException e) {
			return new String(bytes, Charset.forName("windows-1252"));
		}
	}
}
