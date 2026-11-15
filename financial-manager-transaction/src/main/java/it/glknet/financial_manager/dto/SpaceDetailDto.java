package it.glknet.financial_manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import it.glknet.financial_manager.model.Space;
import it.glknet.financial_manager.model.SpaceMovement;

public record SpaceDetailDto(
        Space space,
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal andreaIn,
        BigDecimal rossellaIn,
        BigDecimal externalOut,
        List<TimelinePoint> timeline,
        List<SpaceMovement> movements
) {
    public record TimelinePoint(LocalDate date, BigDecimal balance) {
    }
}
