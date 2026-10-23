package it.glknet.financial_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TextKeywordsTest {

	@Test
	void extractsTheMerchantFromNoisyBankDescriptions() {
		assertEquals("esselunga", TextKeywords.extractKeyword("PAGAMENTO POS ESSELUNGA MILANO 18/10 CARTA 1234"));
		assertEquals("netflix", TextKeywords.extractKeyword("PAYPAL *NETFLIX.COM 4029357733"));
		assertEquals("esselunga", TextKeywords.extractKeyword("Spesa Esselunga")); // descrizione creata da uno scontrino
		assertEquals("caffe", TextKeywords.extractKeyword("Caffè 12"));
		assertNull(TextKeywords.extractKeyword("POS 1234 56 AB")); // niente di significativo
		assertNull(TextKeywords.extractKeyword(null));
	}

	@Test
	void matchesWholeWordsInSequenceOnly() {
		List<String> desc = TextKeywords.tokens("ACQUISTO BARILLA SPA Amazon Prime Video");
		assertFalse(TextKeywords.matches(desc, TextKeywords.tokens("bar"))); // "bar" non e' "barilla"
		assertTrue(TextKeywords.matches(desc, TextKeywords.tokens("barilla")));
		assertTrue(TextKeywords.matches(desc, TextKeywords.tokens("amazon prime")));
		assertFalse(TextKeywords.matches(desc, TextKeywords.tokens("prime amazon"))); // stesso ordine
		assertFalse(TextKeywords.matches(desc, List.of()));
	}

	@Test
	void normalizeLowercasesAndStripsAccentsAndPunctuation() {
		assertEquals("caffe moka", TextKeywords.normalize("  Caffè,  MOKÀ! "));
		assertEquals("", TextKeywords.normalize("***"));
	}
}
