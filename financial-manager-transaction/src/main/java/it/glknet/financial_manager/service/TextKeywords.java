package it.glknet.financial_manager.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Normalizzazione delle descrizioni bancarie e scelta della "parola chiave" che identifica l'esercente. */
public final class TextKeywords {

	/** Parole che compaiono in quasi ogni descrizione bancaria o nelle spese create dagli scontrini: non identificano l'esercente. */
	private static final Set<String> STOP = Set.of("pagamento", "pagamenti", "pos", "carta", "card", "acquisto",
			"acquisti", "addebito", "accredito", "bonifico", "disposto", "favore", "del", "della", "dei", "spa", "srl",
			"srls", "sas", "snc", "italia", "italy", "eur", "euro", "num", "rif", "riferimento", "operazione",
			"transazione", "visa", "mastercard", "maestro", "debit", "credit", "contactless", "apple", "pay", "google",
			"paypal", "satispay", "postepay", "pagobancomat", "bancomat", "commissione", "commissioni", "cro", "trn",
			"ordinante", "beneficiario", "causale", "data", "valuta", "www", "com", "http", "https", "the", "spesa",
			"spese");

	private TextKeywords() {
	}

	/** Minuscolo, senza accenti, solo lettere e cifre: "Caffè  Mokà #12" diventa [caffe, moka, 12]. */
	public static List<String> tokens(String text) {
		if (text == null) {
			return Collections.emptyList();
		}
		String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase()
				.replaceAll("[^a-z0-9]+", " ").trim();
		return plain.isEmpty() ? Collections.emptyList() : Arrays.asList(plain.split(" "));
	}

	/** Forma normalizzata di una parola chiave (anche di piu' parole), o stringa vuota se non ha contenuto. */
	public static String normalize(String keyword) {
		return String.join(" ", tokens(keyword));
	}

	/** La prima parola significativa della descrizione (almeno 3 lettere, non generica), o null. */
	public static String extractKeyword(String description) {
		return tokens(description).stream().filter(t -> t.matches("[a-z]{3,}") && !STOP.contains(t)).findFirst()
				.orElse(null);
	}

	/** Vero se le parole della parola chiave compaiono di seguito nella descrizione (intere parole: "bar" non trova "barilla"). */
	public static boolean matches(List<String> descriptionTokens, List<String> keywordTokens) {
		int n = keywordTokens.size();
		if (n == 0) {
			return false;
		}
		for (int i = 0; i + n <= descriptionTokens.size(); i++) {
			if (descriptionTokens.subList(i, i + n).equals(keywordTokens)) {
				return true;
			}
		}
		return false;
	}
}
