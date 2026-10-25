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
import org.springframework.web.bind.annotation.PutMapping;
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
import it.glknet.financial_manager.service.CategoryRuleService;
import it.glknet.financial_manager.service.CsvImportService;
import it.glknet.financial_manager.service.SavingsImportService;
import it.glknet.financial_manager.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

	private final TransactionService service;
	private final CsvImportService csvImportService;
	private final CategoryRuleService ruleService;
	private final SavingsImportService savingsImport;

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

	@PutMapping("/{id}")
	public Transaction update(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId, @PathVariable UUID id,
			@RequestBody TransactionRequest request) {
		return service.updateTransaction(id, withUser(request, userId));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteTransaction(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@PathVariable UUID id) {
		service.deleteTransaction(id, userId);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Anteprima dell'import: movimenti da importare, versamenti/prelievi dei salvadanai collegati e numero di
	 * spostamenti tra i propri conti/Spaces che non diventano movimenti.
	 */
	public record ImportPreview(List<ImportRow> rows, List<SavingsImportService.SavingsMove> savings,
			int internalTransfers) {
	}

	@PostMapping("/parse-csv")
	public ResponseEntity<ImportPreview> parseCsv(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestParam("file") MultipartFile file) {
		CsvImportService.Parsed parsed = csvImportService.parse(file);
		savingsImport.rememberSources(userId, parsed); // Spaces e IBAN tra cui scegliere nel salvadanaio
		List<SavingsImportService.SavingsMove> savings = new ArrayList<>(
				savingsImport.forInternal(userId, parsed.internal()));
		List<SavingsImportService.RowSavings> perRow = savingsImport.forRows(userId, parsed.rows(), parsed.sources());
		List<TransactionRequest> rows = new ArrayList<>();
		for (int i = 0; i < parsed.rows().size(); i++) {
			SavingsImportService.RowSavings rs = perRow.get(i);
			if (rs != null) {
				savings.add(rs.move());
			}
			if (rs == null || rs.alsoTransaction()) { // i bonifici verso il conto risparmi non sono spese
				rows.add(parsed.rows().get(i));
			}
		}
		List<String> duplicates = service.findDuplicates(userId, rows);
		List<CategoryRuleService.Suggestion> suggestions = ruleService.suggest(userId, rows);
		List<ImportRow> preview = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			TransactionRequest r = rows.get(i);
			CategoryRuleService.Suggestion s = suggestions.get(i);
			preview.add(new ImportRow(r.description(), r.amount(), r.type(), s != null ? s.categoryId() : r.categoryId(),
					r.transactionDate(), duplicates.get(i) != null, duplicates.get(i),
					s != null ? "Regola: " + s.keyword() : null));
		}
		savings.sort(java.util.Comparator.comparing(SavingsImportService.SavingsMove::date));
		return ResponseEntity.ok(new ImportPreview(preview, savings, parsed.internalTransfers()));
	}

	@PostMapping("/bulk-import")
	public ResponseEntity<ImportResult> bulkImport(@RequestAttribute(AuthInterceptor.USER_ID) UUID userId,
			@RequestBody List<TransactionRequest> requests,
			@RequestParam(defaultValue = "false") boolean learn) {
		List<TransactionRequest> rows = requests.stream().map(r -> withUser(r, userId)).toList();
		ImportResult result = service.importAll(userId, rows);
		if (learn) {
			ruleService.learn(userId, rows); // ricorda le categorie scelte per i prossimi import
		}
		return ResponseEntity.ok(result);
	}

	private static TransactionRequest withUser(TransactionRequest r, UUID userId) {
		return new TransactionRequest(r.description(), r.amount(), r.type(), r.categoryId(), userId,
				r.transactionDate());
	}

}
