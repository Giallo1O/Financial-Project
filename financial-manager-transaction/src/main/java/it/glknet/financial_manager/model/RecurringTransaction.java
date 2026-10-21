package it.glknet.financial_manager.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "recurring_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "recurrence_interval")
    @Builder.Default
    private String recurrenceInterval = "MONTHLY";

    @Column(name = "day_of_month")
    @Builder.Default
    private Integer dayOfMonth = 1;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "auto_confirm")
    @Builder.Default
    private boolean autoConfirm = false;

    @Builder.Default
    private boolean active = true;

    @Column(name = "last_generated_date")
    private LocalDate lastGeneratedDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (startDate == null) {
            startDate = LocalDate.now();
        }
        if (nextDueDate == null) {
            int day = (dayOfMonth != null && dayOfMonth >= 1 && dayOfMonth <= 31) ? dayOfMonth : startDate.getDayOfMonth();
            int maxDays = startDate.lengthOfMonth();
            int validDay = Math.min(day, maxDays);
            LocalDate candidate = startDate.withDayOfMonth(validDay);
            if (candidate.isBefore(LocalDate.now())) {
                LocalDate nextMonth = startDate.plusMonths(1);
                candidate = nextMonth.withDayOfMonth(Math.min(day, nextMonth.lengthOfMonth()));
            }
            nextDueDate = candidate;
        }
    }
}
