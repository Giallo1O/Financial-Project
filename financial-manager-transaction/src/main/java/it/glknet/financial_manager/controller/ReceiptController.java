package it.glknet.financial_manager.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import it.glknet.financial_manager.config.AuthInterceptor;
import it.glknet.financial_manager.dto.CategorySpend;
import it.glknet.financial_manager.dto.PricePoint;
import it.glknet.financial_manager.dto.ProductSummary;
import it.glknet.financial_manager.dto.ReceiptDetail;
import it.glknet.financial_manager.dto.ReceiptRequest;
import it.glknet.financial_manager.dto.ScannedReceipt;
import it.glknet.financial_manager.model.Receipt;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.service.ReceiptScanService;
import it.glknet.financial_manager.service.ReceiptService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {

	private final ReceiptScanService scanService;
	private final ReceiptService service;

	/** Legge uno scontrino (1-4 file) e restituisce una bozza da controllare: non salva nulla. */
	@PostMapping("/scan")
	public ScannedReceipt scan(@RequestParam("file") List<MultipartFile> files) {
		return scanService.scan(files);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReceiptDetail save(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestBody ReceiptRequest request) {
		return service.save(userId, request);
	}

	/** Movimenti gia' presenti che potrebbero essere questo scontrino (stesso importo, data vicina). */
	@GetMapping("/match")
	public List<Transaction> match(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam BigDecimal total) {
		return service.matchCandidates(userId, date, total);
	}

	@GetMapping
	public List<Receipt> list(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		return service.list(userId);
	}

	@GetMapping("/{id}")
	public ReceiptDetail get(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id) {
		return service.get(userId, id);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@PathVariable UUID id) {
		service.delete(userId, id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/products")
	public List<ProductSummary> products(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId) {
		return service.products(userId);
	}

	@GetMapping("/products/history")
	public List<PricePoint> history(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestParam String name, @RequestParam(defaultValue = "pz") String unit) {
		return service.history(userId, name, unit);
	}

	@GetMapping("/spending")
	public List<CategorySpend> spending(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return service.spending(userId, from, to);
	}
}
