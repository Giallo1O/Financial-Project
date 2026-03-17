package it.glknet.financial_manager.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import it.glknet.financial_manager.dto.CategoryDeleteEvent;
import it.glknet.financial_manager.dto.TransactionDeleteEvent;
import it.glknet.financial_manager.dto.TransactionEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {

	private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

	// KafkaTemplate<Chiave, Valore>
	private final KafkaTemplate<String, Object> kafkaTemplate;

	private static final String CREATE_TOPIC = "transactions-topic";
	private static final String DELETE_TOPIC = "transactions-delete-topic";
	private static final String CATEGORY_DELETE_TOPIC = "category-delete-topic";

	public void sendTransactionEvent(TransactionEvent event) {
		log.info("Invio evento a Kafka: {}", event);

		// "transactions-topic" è il nome del canale su cui scriviamo
		kafkaTemplate.send(CREATE_TOPIC, event.userId().toString(), event);
	}

	public void sendTransactionDeleteEvent(TransactionDeleteEvent event) {
		log.info("Invio evento di ELIMINAZIONE a Kafka: {}", event);
		kafkaTemplate.send(DELETE_TOPIC, event);
	}

	public void sendCategoryDeleteEvent(CategoryDeleteEvent event) {
		log.info("Invio evento di ELIMINAZIONE CATEGORIA a Kafka: {}", event);
		kafkaTemplate.send(CATEGORY_DELETE_TOPIC, event.userId().toString(), event);
	}
}