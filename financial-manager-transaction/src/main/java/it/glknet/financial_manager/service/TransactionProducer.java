package it.glknet.financial_manager.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.glknet.financial_manager.dto.TransactionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TransactionProducer {

	private static final Logger log = LoggerFactory.getLogger(TransactionProducer.class);

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;

	public TransactionProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = objectMapper;
	}

	private static final String TOPIC = "transaction.import.requests";

	public void sendImportRequest(TransactionRequest request) {
		try {
			String message = objectMapper.writeValueAsString(request);
			kafkaTemplate.send(TOPIC, message);
			log.debug("Sent transaction import request to Kafka: {}", message);
		} catch (Exception e) {
			log.error("Error serializing transaction request", e);
			throw new RuntimeException("Failed to send transaction to Kafka", e);
		}
	}
}
