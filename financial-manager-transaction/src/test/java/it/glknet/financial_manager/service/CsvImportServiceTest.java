package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.TransactionType;

class CsvImportServiceTest {

	@Test
	void parsesDatesAmountsAndTypesInDifferentFormats() {
		String csv = "data,descrizione,importo\n"
				+ "2026-03-01,Stipendio,1500.00\n"
				+ "15/03/2026,\"Spesa Esselunga\",\"-45,50\"\n";
		MockMultipartFile file = new MockMultipartFile("file", "x.csv", "text/csv", csv.getBytes());

		List<TransactionRequest> rows = new CsvImportService().parseCsv(file);

		assertEquals(2, rows.size());
		assertEquals(TransactionType.ENTRATA, rows.get(0).type());
		assertEquals(0, new BigDecimal("1500.00").compareTo(rows.get(0).amount()));
		assertEquals(LocalDate.of(2026, 3, 1), rows.get(0).transactionDate());
		assertEquals(TransactionType.USCITA, rows.get(1).type());
		assertEquals(0, new BigDecimal("45.50").compareTo(rows.get(1).amount()));
		assertEquals(LocalDate.of(2026, 3, 15), rows.get(1).transactionDate());
		assertEquals("Spesa Esselunga", rows.get(1).description());
	}
}
