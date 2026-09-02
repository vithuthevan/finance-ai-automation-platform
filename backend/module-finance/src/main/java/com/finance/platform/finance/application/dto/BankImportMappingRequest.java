package com.finance.platform.finance.application.dto;

import com.finance.platform.finance.application.bankimport.CsvColumnMapping;

public record BankImportMappingRequest(
		int dateColumn,
		int descriptionColumn,
		int referenceColumn,
		int debitColumn,
		int creditColumn,
		int balanceColumn,
		Integer amountColumn,
		String dateFormat,
		boolean headerRow,
		String profileName
) {
	public CsvColumnMapping toMapping() {
		return new CsvColumnMapping(
				dateColumn,
				descriptionColumn,
				referenceColumn,
				debitColumn,
				creditColumn,
				balanceColumn,
				amountColumn,
				dateFormat == null ? "AUTO" : dateFormat,
				headerRow);
	}
}
