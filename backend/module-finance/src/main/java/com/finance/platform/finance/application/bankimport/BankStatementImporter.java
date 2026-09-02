package com.finance.platform.finance.application.bankimport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface BankStatementImporter {

	ImportPreview preview(byte[] content, CsvColumnMapping mapping);

	ImportParseResult parse(byte[] content, CsvColumnMapping mapping);
}
