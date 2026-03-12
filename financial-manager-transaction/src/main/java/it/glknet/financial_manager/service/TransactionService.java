package it.glknet.financial_manager.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.glknet.financial_manager.dto.TransactionDeleteEvent;
import it.glknet.financial_manager.dto.TransactionEvent;
import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.Transaction;
import it.glknet.financial_manager.repository.CategoryRepository;
import it.glknet.financial_manager.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionService {

	private final TransactionRepository repository;

	private final CategoryRepository categoryRepository;

	private final KafkaProducerService kafkaProducerService;

	@Transactional
	public Transaction createTransaction(TransactionRequest request) {
		// Controllo se esiste la categoria
		categoryRepository.findById(request.categoryId())
				.orElseThrow(() -> new IllegalArgumentException("Errore: La categoria inserita non esiste!"));

		// 1. Salvataggio su DB
		Transaction transaction = Transaction.builder().description(request.description()).amount(request.amount())
				.type(request.type()).categoryId(request.categoryId()).userId(request.userId())
				.transactionDate(request.transactionDate()).build();

		Transaction saved = repository.save(transaction);

		// 2. Creazione evento e invio a Kafka
		TransactionEvent event = new TransactionEvent(saved.getId(), saved.getDescription(), saved.getAmount(),
				saved.getType(), saved.getCategoryId(), saved.getUserId(), saved.getTransactionDate());

		kafkaProducerService.sendTransactionEvent(event);

		return saved;
	}

	public List<Transaction> getAllTransactions(String userId) {
		return repository.findByUserId(userId);
	}

	public List<Transaction> getTransactionsByMonth(String userId, int month, int year) {
		return repository.findByUserIdAndMonthAndYear(userId, month, year);
	}

	public void deleteTransaction(UUID id) {
		// 1. Trova la transazione (serve per sapere importo e categoria da stornare)
		Transaction tx = repository.findById(id).orElseThrow(() -> new RuntimeException("Transazione non trovata"));

		// 2. Elimina dal Database
		repository.delete(tx);

		// 3. Crea l'evento e spediscilo su Kafka
		TransactionDeleteEvent event = new TransactionDeleteEvent(tx.getId(), tx.getAmount(), tx.getType().name(),
				tx.getCategoryId(), tx.getUserId());
		kafkaProducerService.sendTransactionDeleteEvent(event);
	}
}
