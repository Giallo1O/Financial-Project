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

	private static List<TransactionRequest> parse(String csv) {
		return new CsvImportService().parseCsv(new MockMultipartFile("file", "x.csv", "text/csv", csv.getBytes()));
	}

	@Test
	void n26UsesColumnNamesNotGuesses() {
		List<TransactionRequest> rows = parse("""
				"Booking Date","Value Date","Partner Name","Partner Iban",Type,"Payment Reference","Account Name","Amount (EUR)","Original Amount","Original Currency","Exchange Rate"
				"2026-10-15","2026-10-15","Lidl Italia","",Presentment,"","Main Account",-23.40,"","",""
				"2026-10-16","2026-10-16","Mario Rossi","IT60X0542811101000000123456",Credit Transfer,"Rimborso","Main Account",45.00,"","",""
				"2026-10-17","2026-10-17","Amazon US","",Presentment,"","Main Account",-18.52,-20.00,USD,1.08
				"2026-10-18","2026-10-18","","",Direct Debit,"Netflix abbonamento","Main Account",-13.99,"","",""
				""");

		assertEquals(4, rows.size());
		assertEquals("Lidl Italia", rows.get(0).description());
		assertEquals("Mario Rossi", rows.get(1).description());
		assertEquals(TransactionType.ENTRATA, rows.get(1).type());
		assertEquals("Amazon US", rows.get(2).description());
		assertEquals(0, new BigDecimal("18.52").compareTo(rows.get(2).amount())); // non il tasso di cambio
		assertEquals(TransactionType.USCITA, rows.get(2).type());
		assertEquals("Netflix abbonamento", rows.get(3).description()); // senza partner: riferimento
	}

	@Test
	void italianBankWithSemicolonsThousandsAndInOutColumns() {
		List<TransactionRequest> rows = parse("""
				Data contabile;Data valuta;Descrizione;Entrate;Uscite
				01/10/2026;01/10/2026;Stipendio ottobre;1.850,00;
				03/10/2026;03/10/2026;Affitto;;-1.200,50
				31/10/2026;;Saldo finale;;
				""");

		assertEquals(2, rows.size());
		assertEquals(0, new BigDecimal("1850.00").compareTo(rows.get(0).amount()));
		assertEquals(TransactionType.ENTRATA, rows.get(0).type());
		assertEquals(0, new BigDecimal("1200.50").compareTo(rows.get(1).amount()));
		assertEquals(TransactionType.USCITA, rows.get(1).type());
		assertEquals(LocalDate.of(2026, 10, 3), rows.get(1).transactionDate());
	}

	@Test
	void n26TransfersBetweenMainAccountAndSpacesAreSkippedAndCounted() {
		String csv = """
				"Booking Date","Value Date","Partner Name","Partner Iban",Type,"Payment Reference","Account Name","Amount (EUR)","Original Amount","Original Currency","Exchange Rate"
				2026-01-05,2026-01-05,Vacanze,,"Debit Transfer","Regola mensile","Conto corrente principale",-200.00,,,
				2026-01-05,2026-01-05,"Conto corrente principale",,"Credit Transfer","Regola mensile",Vacanze,200.00,,,
				2026-01-06,2026-01-06,"Conto corrente principale",,"Debit Transfer",Spotify,Abbonamenti,-20.99,,,
				2026-01-07,2026-01-07,SPOTIFY,,Presentment,,Abbonamenti,-20.99,,,
				2026-01-08,2026-01-08,"BENNET        12",,Presentment,,"Conto corrente principale",-31.40,,,
				""";

		CsvImportService.Parsed parsed = new CsvImportService()
				.parse(new MockMultipartFile("file", "x.csv", "text/csv", csv.getBytes()));

		assertEquals(3, parsed.internalTransfers()); // i due lati del giroconto e il trasferimento verso lo Space
		assertEquals(List.of("SPOTIFY", "BENNET 12"), parsed.rows().stream().map(TransactionRequest::description).toList());
	}
}
