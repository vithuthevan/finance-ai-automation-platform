package com.finance.platform.finance.domain.model;

import com.finance.platform.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clients", uniqueConstraints = @UniqueConstraint(columnNames = {"firm_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client extends TenantAwareEntity {

	@Column(nullable = false, length = 200)
	private String name;

	@Column(length = 50)
	private String businessRegNo;

	@Column(length = 255)
	private String contactEmail;

	@Column(nullable = false)
	@Builder.Default
	private boolean active = true;

	private Instant deletedAt;

	@OneToMany(mappedBy = "client")
	@Builder.Default
	private List<Expense> expenses = new ArrayList<>();

	@OneToMany(mappedBy = "client")
	@Builder.Default
	private List<Income> incomes = new ArrayList<>();

	@OneToMany(mappedBy = "client")
	@Builder.Default
	private List<Receipt> receipts = new ArrayList<>();
}
