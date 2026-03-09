package it.glknet.financial_manager.service;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
					// We apply a basic heuristic parser. If the CSV has Date, Desc, Amount:
					// Usually: Date (0), Description (1), Amount (2)
					// (A more sophisticated version would let the user map columns in UI or here)
					String description = "";
					BigDecimal amount = BigDecimal.ZERO;
					TransactionType type = TransactionType.USCITA; // Default

					// Searching for the amount (usually a number with decimals)
					for (String col : row) {
						if (col == null || col.isBlank())
							continue;

						String cleanCol = col.replace("\"", "").trim();

						// Try to parse amount
						if (isNumeric(cleanCol)) {
							amount = new BigDecimal(cleanCol.replace(",", "."));
							if (amount.compareTo(BigDecimal.ZERO) > 0) {
								type = TransactionType.ENTRATA;
							} else {
								type = TransactionType.USCITA;
								amount = amount.abs();
							}
						} else if (!isDate(cleanCol) && description.length() < cleanCol.length()) { // Guess description
																									// as the longest
																									// text string
							description = cleanCol;
						}
					}

					// If amount is 0 and no description, likely an empty line
					if (amount.equals(BigDecimal.ZERO) && description.isBlank()) {
						continue;
					}

					TransactionRequest req = new TransactionRequest(
							description.isEmpty() ? "Imported Transaction" : description,
							amount,
							type,
							null, // Category is unknown yet
							"default-user" // Realistically we should get this from security context or parameter
					);
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

	private boolean isNumeric(String str) {
		if (str == null || str.isBlank())
			return false;
		String clean = str.replace(".", "").replace(",", "").replace("-", "").trim();
		return clean.chars().allMatch(Character::isDigit);
	}

	private boolean isDate(String str) {
		// A fast basic check if string looks like a date format (dd/mm/yyyy or
		// yyyy-mm-dd)
		return str.matches("^\\d{2,4}[/-]\\d{2}[/-]\\d{2,4}.*");
	}
}
