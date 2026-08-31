export interface CloseFinding {
  severity: 'BLOCKER' | 'WARNING' | 'INFO' | 'DISABLED';
  code: string;
  message: string;
  count: number;
  actionHint: string;
}

export interface CloseChecklistItem {
  code: string;
  label: string;
  passed: boolean;
  severity: string;
  count: number;
}

export interface PeriodReadiness {
  ready: boolean;
  readinessPercent: number;
  blockers: CloseFinding[];
  warnings: CloseFinding[];
  info: CloseFinding[];
  checklist: CloseChecklistItem[];
  summary: {
    draftExpenses: number;
    draftIncome: number;
    documentsNeedingReview: number;
    failedUnlinkedDocuments: number;
    unlinkedDocuments: number;
    openDocumentRequests: number;
    approvedTransactionsWithoutDocuments: number;
    approvedTransactionsWithDocuments: number;
  };
  ledger: {
    draftExpenses: number;
    approvedExpenses: number;
    voidExpenses: number;
    draftExpenseAmount: number;
    approvedExpenseAmount: number;
    draftIncome: number;
    approvedIncome: number;
    voidIncome: number;
    draftIncomeAmount: number;
    approvedIncomeAmount: number;
    netApproved: number;
  };
  documents: {
    total: number;
    linked: number;
    unlinked: number;
    needsReview: number;
    rejected: number;
    failed: number;
    processed: number;
  };
}

export interface AccountingPeriod {
  id: string;
  clientId: string;
  clientName: string;
  year: number;
  month: number;
  startDate: string;
  endDate: string;
  status: string;
  ready: boolean;
  readinessPercent: number;
  blockerCount: number;
  blockers: CloseFinding[];
  reviewStartedAt?: string | null;
  reviewStartedByName?: string | null;
  closedAt?: string | null;
  closedByName?: string | null;
  closeNote?: string | null;
  reopenedAt?: string | null;
  reopenedByName?: string | null;
  reopenReason?: string | null;
}

export interface WorkQueueItem {
  clientId: string;
  clientName: string;
  periodId: string | null;
  year: number;
  month: number;
  startDate: string;
  endDate: string;
  status: string;
  displayStatus: string;
  ready: boolean;
  readinessPercent: number;
  blockerCount: number;
}

export interface DocumentRequestRow {
  id: string;
  clientId: string;
  periodId?: string | null;
  description: string;
  documentType: string;
  dueDate?: string | null;
  status: string;
  uploadedDocumentId?: string | null;
}
