package it.glknet.financial_manager.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.glknet.financial_manager.dto.TransactionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TransactionImportListener {

	private static final Logger log = LoggerFactory.getLogger(TransactionImportListener.class);

	private final ObjectMapper objectMapper;
	private final TransactionService transactionService;

	public TransactionImportListener(ObjectMapper objectMapper, TransactionService transactionService) {
		this.objectMapper = objectMapper;
		this.transactionService = transactionService;
	}

	private static final String TOPIC = "transaction.import.requests";
	private static final String GROUP_ID = "transaction-service-group";

	@KafkaListener(topics = TOPIC, groupId = GROUP_ID)
	public void consumeImportRequest(String message) {
		try {
			log.debug("Received transaction import request from Kafka: {}", message);
			TransactionRequest request = objectMapper.readValue(message, TransactionRequest.class);
			
			// Use the existing transaction service to save the transaction to the database
			transactionService.createTransaction(request);
			log.info("Successfully imported transaction: {}", request.description());
			
			// The existing TransactionService already handles publishing "transaction.created" event inside createTransaction method.
		} catch (Exception e) {
			log.error("Failed to process transaction import message: {}", message, e);
			// In a real production system, consider a Dead Letter Queue (DLQ) here
		}
	}
}
