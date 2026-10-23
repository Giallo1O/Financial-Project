package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ShoppingItemView(UUID id, String name, BigDecimal quantity, String unit, boolean checked,
		String addedBy, PriceHint price) {
}
