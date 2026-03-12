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
		log.info(">>> [Kafka] Ricevuto TransactionEvent: userId={}, categoryId={}, type={}, amount={}",
				event.userId(), event.categoryId(), event.type(), event.amount());
		// Cerchiamo la categoria nel DB, se non esiste ne creiamo una nuova
		CategorySummary summary = repository.findByUserIdAndCategoryId(event.userId(), event.categoryId())
				.orElseGet(() -> {
					CategorySummary newSummary = new CategorySummary();
					newSummary.setUserId(event.userId());
					newSummary.setCategoryId(event.categoryId());
					newSummary.setTotalAmount(BigDecimal.ZERO);
					newSummary.setTransactionCount(0L);
					return newSummary;
				});

		// Per il grafico "Spese per Categoria", accumuliamo solo le USCITE come valori
		// positivi
		if ("USCITA".equals(event.type())) {
			summary.setTotalAmount(summary.getTotalAmount().add(event.amount()));
			summary.setTransactionCount(summary.getTransactionCount() + 1);
			summary.setLastUpdate(LocalDateTime.now());
			repository.save(summary);
			log.info(">>> [Kafka] Statistiche aggiornate per userId={}, categoryId={}", event.userId(), event.categoryId());
		}
	}

	@KafkaListener(topics = "transactions-delete-topic", groupId = "dashboard-group-v2")
	public void handleTransactionDelete(TransactionDeleteEvent event) {
		log.info("Ricevuto evento di eliminazione per categoria: {}", event.categoryId());

		repository.findByUserIdAndCategoryId(event.userId(), event.categoryId()).ifPresent(summary -> {
			// Operazione inversa: se era un'USCITA, la sottraiamo dal totale
			if ("USCITA".equals(event.type())) {
				summary.setTotalAmount(summary.getTotalAmount().subtract(event.amount()));
				summary.setTransactionCount(summary.getTransactionCount() - 1);
				summary.setLastUpdate(LocalDateTime.now());
				repository.save(summary);
				log.info("Totali aggiornati dopo eliminazione.");
			}
		});
	}

	@KafkaListener(topics = "category-delete-topic", groupId = "dashboard-group-v2")
	public void handleCategoryDelete(CategoryDeleteEvent event) {
		log.info(">>> [Kafka] Ricevuto CategoryDeleteEvent per categoryId={}", event.categoryId());
		repository.findByUserIdAndCategoryId(event.userId(), event.categoryId()).ifPresent(summary -> {
			repository.delete(summary);
			log.info(">>> [Kafka] CategorySummary eliminata per userId={}, categoryId={}", event.userId(), event.categoryId());
		});
	}
}
