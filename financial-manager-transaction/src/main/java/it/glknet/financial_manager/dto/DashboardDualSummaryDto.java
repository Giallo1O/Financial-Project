package it.glknet.financial_manager.dto;

import java.math.BigDecimal;

public record DashboardDualSummaryDto(
        int month,
        int year,
        BigDecimal generalIncome,
        BigDecimal generalExpenses,
        BigDecimal generalBalance,
        BigDecimal personalIncome,
        BigDecimal personalExpenses,
        BigDecimal personalBalance,
        BigDecimal rossellaTotalContribution,
        BigDecimal andreaReimbursements
) {
}
