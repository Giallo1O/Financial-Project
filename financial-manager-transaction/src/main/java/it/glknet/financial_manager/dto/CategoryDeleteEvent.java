package it.glknet.financial_manager.dto;

import java.util.UUID;

public record CategoryDeleteEvent(UUID categoryId, String userId, boolean expense) {
}
