export interface BankAccount {
  id: string;
  clientId: string;
  bankName: string;
  accountName: string;
  maskedAccountNumber: string;
  currency: string;
  active: boolean;
}

export interface BankImportPreview {
  rowsDetected: number;
  validRows: number;
  invalidRows: number;
  potentialDuplicates: number;
  periodFrom?: string | null;
  periodTo?: string | null;
  sampleRows: Array<{
    lineNumber: number;
    txnDate: string;
    description: string;
    referenceNo?: string | null;
    debit?: number | null;
    credit?: number | null;
    balance?: number | null;
  }>;
  errors: Array<{ lineNumber: number; message: string }>;
}

export interface BankImportRow {
  id: string;
  fileName: string;
  importedCount: number;
  duplicateCount: number;
  failedCount: number;
  importStatus: string;
  periodFrom?: string | null;
  periodTo?: string | null;
  createdAt?: string;
}

export interface MatchSuggestion {
  matchId?: string | null;
  ledgerType: string;
  ledgerId: string;
  ledgerLabel: string;
  amount: number;
  transactionDate: string;
  description?: string | null;
  score: number;
  confidence: string;
  status: string;
  scoreComponents?: { label: string; points: number }[];
}

export interface BankTransactionRow {
  id: string;
  bankAccountId?: string | null;
  txnDate: string;
  description?: string | null;
  referenceNo?: string | null;
  debit?: number | null;
  credit?: number | null;
  balance?: number | null;
  currency?: string;
  direction?: string;
  matchStatus: string;
  ignoreReason?: string | null;
  pendingExpenseId?: string | null;
  pendingIncomeId?: string | null;
  suggestions: MatchSuggestion[];
}

export interface ReconciliationSummary {
  totalTransactions: number;
  matched: number;
  suggested: number;
  unmatched: number;
  ignored: number;
  pendingApproval: number;
  reconciliationPercent: number;
}

export interface CategoryOption {
  id: string;
  name: string;
}
