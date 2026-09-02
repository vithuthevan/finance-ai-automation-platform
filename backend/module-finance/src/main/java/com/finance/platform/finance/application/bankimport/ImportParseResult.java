package com.finance.platform.finance.application.bankimport;

import java.util.List;

public record ImportParseResult(List<ParsedBankRow> validRows, List<ImportRowError> errors) {
}
