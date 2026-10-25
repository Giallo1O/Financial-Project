package it.glknet.financial_manager.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "savings_goals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE) // privato: Jackson usa il costruttore vuoto e non rompe i default
@Builder
public class SavingsGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "target_amount", nullable = false)
    private BigDecimal targetAmount;

    @Column(name = "current_amount", nullable = false)
    @Builder.Default
    private BigDecimal currentAmount = BigDecimal.ZERO;

    @Column(name = "target_date")
    private LocalDate targetDate;

    private String color;

    private String icon;

    @Builder.Default
    private boolean completed = false;

    /**
     * Da dove arrivano i soldi, per aggiornare il salvadanaio con gli import: "SPACE" (uno Space N26, sourceKey e' il
     * suo nome) o "IBAN" (un conto esterno, sourceKey e' l'IBAN). Null: salvadanaio gestito a mano.
     */
    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_key")
    private String sourceKey;

    /** Come mostrarlo, es. "Andrea Gallicchio (IT13...)" o "Vacanze". */
    @Column(name = "source_label")
    private String sourceLabel;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
