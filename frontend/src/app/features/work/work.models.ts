export interface WorkSummary {
  documentsToReview: number;
  processingFailures: number;
  pendingApprovals: number;
  openDocumentRequests: number;
  overdueDocumentRequests: number;
  bankItemsUnresolved: number;
  clientsReadyToClose: number;
}

export interface WorkItem {
  type: string;
  priority: string;
  clientId: string;
  clientName: string;
  title: string;
  description: string;
  resourceId?: string | null;
  actionUrl: string;
  dueDate?: string | null;
  overdue: boolean;
  createdAt?: string | null;
  assignedUserId?: string | null;
  assignedUserName?: string | null;
}

export interface ClientPortfolioItem {
  clientId: string;
  clientName: string;
  primaryAccountantUserId?: string | null;
  primaryAccountantName?: string | null;
  documentsNeedingReview: number;
  bankUnresolved: number;
  bankReconciliationPercent?: number | null;
  closeStatus: string;
  readyToClose: boolean;
  readinessPercent: number;
}
