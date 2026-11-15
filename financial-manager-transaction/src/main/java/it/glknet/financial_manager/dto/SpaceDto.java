package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SpaceDto(
        UUID id,
        String name,
        String icon,
        String color,
        BigDecimal currentBalance,
        boolean shared,
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal andreaIn,
        BigDecimal rossellaIn,
        int movementCount
) {
}
