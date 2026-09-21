export type MonthEndPortfolioState = 'READY' | 'ATTENTION' | 'BLOCKED' | 'CLOSED';

export interface CloseActionLink {
  label: string;
  path: string;
  query: Record<string, string>;
}

export type CloseResponsibility = 'CLIENT' | 'TEAM';

export interface MonthEndBlocker {
  severity: 'BLOCKER' | 'WARNING' | 'INFO';
  code: string;
  message: string;
  count: number;
  actionHint: string;
  responsibility: CloseResponsibility;
  responsibilityLabel: string;
  action: CloseActionLink;
}

export interface MonthEndProgressStep {
  code: string;
  label: string;
  indicator: string;
}

export interface MonthEndClientRow {
  clientId: string;
  clientName: string;
  periodId: string | null;
  year: number;
  month: number;
  periodStart: string;
  periodEnd: string;
  state: MonthEndPortfolioState;
  stateLabel: string;
  readyToClose: boolean;
  readinessPercent: number;
  overdueDocumentRequests: number;
  waitingOnClientItems: number;
  teamActionItems: number;
  primaryAccountantUserId: string | null;
  primaryAccountantName: string | null;
  progress: MonthEndProgressStep[];
  blockers: MonthEndBlocker[];
  primaryAction: CloseActionLink;
}

export interface MonthEndCommandCenter {
  year: number;
  month: number;
  periodLabel: string;
  summary: {
    totalClients: number;
    ready: number;
    needsAttention: number;
    blocked: number;
    closed: number;
    overdueDocumentRequests: number;
    openDocumentRequests: number;
  };
  clients: MonthEndClientRow[];
}

export interface OnboardingChecklist {
  firmId: string;
  firmName: string | null;
  steps: { code: string; label: string; completed: boolean; actionPath: string }[];
  completedCount: number;
  totalCount: number;
}
