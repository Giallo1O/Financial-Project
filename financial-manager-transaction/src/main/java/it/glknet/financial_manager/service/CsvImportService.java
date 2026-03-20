package it.glknet.financial_manager.service;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.TransactionType;

@Service
public class CsvImportService {

	private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

	public List<TransactionRequest> parseCsv(MultipartFile file) {
		List<TransactionRequest> requests = new ArrayList<>();

		try (Reader reader = new InputStreamReader(file.getInputStream())) {
			CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build(); // Skip header
			List<String[]> rows = csvReader.readAll();

			for (String[] row : rows) {
				try {
					String description = "";
					BigDecimal amount = BigDecimal.ZERO;
					TransactionType type = TransactionType.USCITA;
					LocalDate date = LocalDate.now();

					for (String col : row) {
						if (col == null || col.isBlank())
							continue;

						String cleanCol = col.replace("\"", "").trim();

						// 1. Check if it's a date first to avoid it being parsed as a number
						if (isDate(cleanCol)) {
							LocalDate parsedDate = tryParseDate(cleanCol);
							if (parsedDate != null) {
								date = parsedDate;
								continue;
							}
						}

						// 2. Try to parse amount
						if (isNumeric(cleanCol)) {
							try {
								amount = new BigDecimal(cleanCol.replace(",", "."));
								if (amount.compareTo(BigDecimal.ZERO) > 0) {
									type = TransactionType.ENTRATA;
								} else {
									type = TransactionType.USCITA;
									amount = amount.abs();
								}
								continue;
							} catch (Exception e) {
								// Not a number then
							}
						}

						// 3. Guess description as the longest surviving string
						if (description.length() < cleanCol.length()) {
							description = cleanCol;
						}
					}

					// If amount is 0 and no description, likely an empty line
					if (amount.equals(BigDecimal.ZERO) && description.isBlank()) {
						continue;
					}

					// We leave categoryId and userId as null so the frontend can fill them
					TransactionRequest req = new TransactionRequest(
							description.isEmpty() ? "Imported Transaction" : description, amount, type,
							null, null, date);
					requests.add(req);

				} catch (Exception e) {
					log.warn("Skipping row due to parsing error: {}", e.getMessage());
				}
			}

		} catch (Exception e) {
			log.error("Failed to parse CSV file", e);
			throw new RuntimeException("Failed to parse CSV: " + e.getMessage());
		}

		return requests;
	}

	private LocalDate tryParseDate(String str) {
		try {
			// Basic support for common formats: yyyy-MM-dd, dd/MM/yyyy, dd-MM-yyyy
			if (str.contains("-")) {
				String[] parts = str.split("-");
				if (parts[0].length() == 4) {
					return LocalDate.parse(str); // yyyy-MM-dd
				} else {
					return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]),
							Integer.parseInt(parts[0]));
				}
			} else if (str.contains("/")) {
				String[] parts = str.split("/");
				return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
			}
		} catch (Exception e) {
			// ignore
		}
		return null;
	}

	private boolean isNumeric(String str) {
		if (str == null || str.isBlank())
			return false;
		// Regex to check if it's a number (including decimals and signs)
		return str.matches("^-?\\d+([.,]\\d+)?$");
	}

	private boolean isDate(String str) {
		return str.matches("^\\d{2,4}[/-]\\d{2}[/-]\\d{2,4}.*");
	}
}
