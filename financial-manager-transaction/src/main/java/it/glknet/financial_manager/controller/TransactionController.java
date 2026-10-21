package it.glknet.financial_manager.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import it.glknet.financial_manager.config.AuthInterceptor;
import it.glknet.financial_manager.dto.ImportResult;
import it.glknet.financial_manager.dto.ImportRow;
import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.service.CsvImportService;
import it.glknet.financial_manager.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

	private final TransactionService service;
	private final CsvImportService csvImportService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Transaction create(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestBody TransactionRequest request) {
		return service.createTransaction(withUser(request, userId));
	}

	/**
	 * GET /api/transactions
	 * GET /api/transactions?month=3&year=2026
	 * L'utente e' quello del token, non un parametro.
	 */
	@GetMapping
	public List<Transaction> getAll(
			@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year) {

		if (month != null && year != null) {
			return service.getTransactionsByMonth(userId, month, year);
		}
		return service.getAllTransactions(userId);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteTransaction(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@PathVariable UUID id) {
		service.deleteTransaction(id, userId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/parse-csv")
	public ResponseEntity<List<ImportRow>> parseCsv(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestParam("file") MultipartFile file) {
		List<TransactionRequest> rows = csvImportService.parseCsv(file);
		List<Boolean> duplicates = service.markDuplicates(userId, rows);
		List<ImportRow> preview = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			TransactionRequest r = rows.get(i);
			preview.add(new ImportRow(r.description(), r.amount(), r.type(), r.categoryId(), r.transactionDate(),
					duplicates.get(i)));
		}
		return ResponseEntity.ok(preview);
	}

	@PostMapping("/bulk-import")
	public ResponseEntity<ImportResult> bulkImport(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestBody List<TransactionRequest> requests) {
		return ResponseEntity.ok(service.importAll(userId, requests.stream().map(r -> withUser(r, userId)).toList()));
	}

	private static TransactionRequest withUser(TransactionRequest r, UUID userId) {
		return new TransactionRequest(r.description(), r.amount(), r.type(), r.categoryId(), userId,
				r.transactionDate());
	}

}
