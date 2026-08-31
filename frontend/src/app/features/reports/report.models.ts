export interface CategoryAmount {
  categoryId: string;
  categoryCode: string;
  categoryName: string;
  amount: string | number;
  percentageOfTotal: string | number;
  parentId?: string | null;
  parentName?: string | null;
}

export interface NamedAmount {
  name: string;
  amount: string | number;
  count: number;
  percentageOfTotal: string | number;
}

export interface PlSummary {
  clientId: string;
  clientName: string;
  from: string;
  to: string;
  currencyCode: string;
  hasApprovedData: boolean;
  resultType: 'PROFIT' | 'LOSS' | 'BREAK_EVEN';
  totalIncome: string | number;
  totalExpenses: string | number;
  netResult: string | number;
  incomeByCategory: CategoryAmount[];
  expensesByCategory: CategoryAmount[];
}

export interface PeriodTotals {
  from: string;
  to: string;
  totalIncome: string | number;
  totalExpenses: string | number;
  netResult: string | number;
  resultType: string;
}

export interface PlComparison {
  clientId: string;
  clientName: string;
  currencyCode: string;
  currentPeriod: PeriodTotals;
  previousPeriod: PeriodTotals;
  incomeDifference: string | number;
  expenseDifference: string | number;
  netDifference: string | number;
  incomeChangePercent: string | number | null;
  expenseChangePercent: string | number | null;
  netChangePercent: string | number | null;
}

export interface IncomeSummary {
  clientId: string;
  clientName: string;
  from: string;
  to: string;
  currencyCode: string;
  hasApprovedData: boolean;
  totalIncome: string | number;
  transactionCount: number;
  averageAmount: string | number;
  byCategory: CategoryAmount[];
  byPaymentMethod: NamedAmount[];
}

export interface LargestItem {
  id: string;
  transactionDate: string;
  amount: string | number;
  partyName: string;
  categoryName: string;
}

export interface ExpenseSummary {
  clientId: string;
  clientName: string;
  from: string;
  to: string;
  currencyCode: string;
  hasApprovedData: boolean;
  totalExpenses: string | number;
  transactionCount: number;
  averageAmount: string | number;
  byCategory: CategoryAmount[];
  largestExpenses: LargestItem[];
}

export interface MonthlyTrend {
  monthStart: string;
  income: string | number;
  expenses: string | number;
  profitOrLoss: string | number;
}

export interface TransactionStatusSummary {
  draftExpenses: number;
  approvedExpenses: number;
  voidExpenses: number;
  draftIncome: number;
  approvedIncome: number;
  voidIncome: number;
}

export interface DocumentSupportSummary {
  approvedWithDocuments: number;
  approvedWithoutDocuments: number;
  unlinkedDocuments: number;
  documentsAwaitingReview: number;
}

export interface DashboardSummary {
  clientId: string;
  clientName: string;
  currencyCode: string;
  hasApprovedData: boolean;
  income: string | number;
  expenses: string | number;
  profitOrLoss: string | number;
  resultType: string;
  unreviewedDocuments: number;
  unapprovedTransactions: number;
  unlinkedDocuments: number;
  approvedWithDocuments: number;
  approvedWithoutDocuments: number;
  statusSummary: TransactionStatusSummary;
}

export interface PracticeDashboard {
  activeClients: number;
  clientsWithDrafts: number;
  documentsNeedingReview: number;
  pendingApprovals: number;
  clientsWithRecentActivity: number;
}

export interface ReportTransaction {
  id: string;
  transactionDate: string;
  vendorName?: string;
  customerName?: string;
  amount: string | number;
  currencyCode?: string;
}
