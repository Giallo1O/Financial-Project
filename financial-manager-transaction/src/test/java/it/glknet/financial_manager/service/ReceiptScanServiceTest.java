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
}
