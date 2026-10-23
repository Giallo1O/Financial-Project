package it.glknet.financial_manager.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una persona (oltre al proprietario) che usa una lista. */
@Entity
@Table(name = "shopping_list_members", uniqueConstraints = @UniqueConstraint(columnNames = { "list_id", "user_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ShoppingListMember {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "list_id", nullable = false)
	private UUID listId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	private String username;

	@Column(name = "joined_at")
	private LocalDateTime joinedAt;

	@PrePersist
	protected void onCreate() {
		joinedAt = LocalDateTime.now();
	}
}
