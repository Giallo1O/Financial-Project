package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpaceImportItem(
        String spaceName,
        LocalDate date,
        BigDecimal amount,
        String partnerName,
        String reference
) {
}
