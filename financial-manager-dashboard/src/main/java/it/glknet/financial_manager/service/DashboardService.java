package it.glknet.financial_manager.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import it.glknet.financial_manager.dto.CategoryDeleteEvent;
import it.glknet.financial_manager.dto.TransactionDeleteEvent;
import it.glknet.financial_manager.dto.TransactionEvent;
import it.glknet.financial_manager.model.CategorySummary;
import it.glknet.financial_manager.repository.CategorySummaryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

	private final CategorySummaryRepository repository;

	@Transactional
	@KafkaListener(topics = "transactions-topic", groupId = "dashboard-group-v2")
	public void updateStatistics(TransactionEvent event) {
		log.info(">>> [Kafka] Ricevuto TransactionEvent: userId={}, categoryId={}, type={}, amount={}", event.userId(),
				event.categoryId(), event.type(), event.amount());
		// Cerchiamo la categoria nel DB, se non esiste ne creiamo una nuova
		CategorySummary summary = repository.findByUserIdAndCategoryIdAndMonthAndYear(event.userId(),
				event.categoryId(), event.transactionDate().getMonthValue(), event.transactionDate().getYear())
				.orElseGet(() -> {
					CategorySummary newSummary = new CategorySummary();
					newSummary.setUserId(event.userId());
					newSummary.setCategoryId(event.categoryId());
					newSummary.setMonth(event.transactionDate().getMonthValue());
					newSummary.setYear(event.transactionDate().getYear());
					newSummary.setTotalAmount(BigDecimal.ZERO);
					newSummary.setTransactionCount(0L);
					newSummary.setExpense(event.type().equals("USCITA"));
					return newSummary;
				});

		// Accumuliamo le statistiche
		summary.setTotalAmount(summary.getTotalAmount().add(event.amount()));
		summary.setTransactionCount(summary.getTransactionCount() + 1);
		summary.setLastUpdate(LocalDateTime.now());
		repository.save(summary);
		log.info(">>> [Kafka] Statistiche aggiornate ({}) per userId={}, categoryId={}", event.type(), event.userId(),
				event.categoryId());
	}

	@KafkaListener(topics = "transactions-delete-topic", groupId = "dashboard-group-v2")
	public void handleTransactionDelete(TransactionDeleteEvent event) {
		log.info("Ricevuto evento di eliminazione per categoria: {}", event.categoryId());

		repository
				.findByUserIdAndCategoryIdAndMonthAndYear(event.id(), event.categoryId(),
						event.transactionDate().getMonthValue(), event.transactionDate().getYear())
				.ifPresent(summary -> {
					// Operazione inversa: sottraiamo dal totale
					summary.setTotalAmount(summary.getTotalAmount().subtract(event.amount()));
					summary.setTransactionCount(summary.getTransactionCount() - 1);
					summary.setLastUpdate(LocalDateTime.now());
					repository.save(summary);
					log.info("Totali aggiornati dopo eliminazione di un evento di tipo {}.", event.type());
				});
	}

	@KafkaListener(topics = "category-delete-topic", groupId = "dashboard-group-v2")
	public void handleCategoryDelete(CategoryDeleteEvent event) {
		log.info(">>> [Kafka] Ricevuto CategoryDeleteEvent per categoryId={}", event.categoryId());
		repository.findByUserIdAndCategoryId(event.userId(), event.categoryId()).ifPresent(summary -> {
			repository.delete(summary);
			log.info(">>> [Kafka] CategorySummary eliminata per userId={}, categoryId={}", event.userId(),
					event.categoryId());
		});
	}
}
