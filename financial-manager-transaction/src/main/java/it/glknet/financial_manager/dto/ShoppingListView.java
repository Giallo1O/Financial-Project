package it.glknet.financial_manager.dto;

import java.util.List;
import java.util.UUID;

/**
 * Una lista vista da una persona: mine = e' il proprietario (solo lui vede il codice di invito). homeAssistant = e' la
 * lista della spesa di casa in Home Assistant (niente codici: e' gia' di tutta la casa).
 */
public record ShoppingListView(UUID id, String name, String ownerName, boolean mine, List<String> members,
		String inviteCode, int openItems, int totalItems, boolean homeAssistant) {
}
