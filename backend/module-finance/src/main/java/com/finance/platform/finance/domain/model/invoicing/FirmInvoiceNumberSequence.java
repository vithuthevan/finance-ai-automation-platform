package com.finance.platform.finance.domain.model.invoicing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "firm_invoice_number_sequences")
@Getter
@Setter
public class FirmInvoiceNumberSequence {

	@Id
	@Column(name = "firm_id")
	private UUID firmId;

	@Column(name = "next_number", nullable = false)
	private long nextNumber = 1L;
}
