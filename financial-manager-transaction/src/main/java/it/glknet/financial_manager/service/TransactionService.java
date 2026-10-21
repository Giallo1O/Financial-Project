package it.glknet.financial_manager.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import it.glknet.financial_manager.dto.TransactionDeleteEvent;
import it.glknet.financial_manager.dto.TransactionEvent;
import it.glknet.financial_manager.dto.TransactionRequest;
import it.glknet.financial_manager.model.Category;
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
		// Controllo se esiste la categoria (se fornita)
		if (request.categoryId() != null) {
			Category category = categoryRepository.findById(request.categoryId())
					.orElseThrow(() -> new IllegalArgumentException("Errore: La categoria inserita non esiste!"));
			if (!category.getUserId().equals(request.userId())) {
				throw new IllegalArgumentException("Errore: La categoria inserita non esiste!");
			}
		}

		// 1. Salvataggio su DB
		Transaction transaction = Transaction.builder().description(request.description()).amount(request.amount())
				.type(request.type()).categoryId(request.categoryId()).userId(request.userId())
				.transactionDate(request.transactionDate()).build();

		Transaction saved = repository.save(transaction);

		// 2. Creazione evento e invio a Kafka
		TransactionEvent event = new TransactionEvent(saved.getId(), saved.getDescription(), saved.getAmount(),
				saved.getType(), saved.getCategoryId(), saved.getUserId(), saved.getTransactionDate());

		afterCommit(() -> kafkaProducerService.sendTransactionEvent(event));

		return saved;
	}

	public List<Transaction> getAllTransactions(UUID userId) {
		return repository.findByUserId(userId);
	}

	public List<Transaction> getTransactionsByMonth(UUID userId, int month, int year) {
		return repository.findByUserIdAndMonthAndYear(userId, month, year);
	}

	@Transactional
	public void deleteTransaction(UUID id, UUID userId) {
		// 1. Trova la transazione (serve per sapere importo e categoria da stornare); solo se e' dell'utente
		Transaction tx = repository.findById(id).filter(t -> userId.equals(t.getUserId()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transazione non trovata"));

		// 2. Elimina dal Database
		repository.delete(tx);

		// 3. Crea l'evento e spediscilo su Kafka
		TransactionDeleteEvent event = new TransactionDeleteEvent(tx.getId(), tx.getAmount(), tx.getType().name(),
				tx.getCategoryId(), tx.getUserId(), tx.getTransactionDate());
		afterCommit(() -> kafkaProducerService.sendTransactionDeleteEvent(event));
	}

	// ponytail: se Kafka e' giu' nel momento dell'invio l'evento si perde (serve un outbox); almeno non partono eventi per commit falliti.
	private static void afterCommit(Runnable send) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				send.run();
			}
		});
	}
}
