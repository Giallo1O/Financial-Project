package it.glknet.financial_manager.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.service.CsvImportService;
import it.glknet.financial_manager.service.TransactionProducer;
import it.glknet.financial_manager.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "http://localhost:3000") // Frontend Docker port
@RequiredArgsConstructor
public class TransactionController {

	private final TransactionService service;
	private final CsvImportService csvImportService;
	private final TransactionProducer transactionProducer;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Transaction create(@RequestBody TransactionRequest request) {
		return service.createTransaction(request);
	}

	/**
	 * GET /api/transactions?userId=xxx
	 * GET /api/transactions?userId=xxx&month=3&year=2026
	 */
	@GetMapping
	public List<Transaction> getAll(
			@RequestParam UUID userId,
			@RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer year) {

		if (month != null && year != null) {
			return service.getTransactionsByMonth(userId, month, year);
		}
		return service.getAllTransactions(userId);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteTransaction(@PathVariable UUID id) {
		service.deleteTransaction(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/parse-csv")
	public ResponseEntity<List<TransactionRequest>> parseCsv(@RequestParam("file") MultipartFile file) {
		return ResponseEntity.ok(csvImportService.parseCsv(file));
	}

	@PostMapping("/bulk-import")
	public ResponseEntity<Void> bulkImport(@RequestBody List<TransactionRequest> requests) {
		for (TransactionRequest request : requests) {
			transactionProducer.sendImportRequest(request);
		}
		return ResponseEntity.accepted().build();
	}

}
