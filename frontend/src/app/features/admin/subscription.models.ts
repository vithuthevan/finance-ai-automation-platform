export interface UsageLimit {
  used: number;
  limit: number;
  percentUsed: number;
  overLimit: boolean;
}

export interface SubscriptionUsage {
  planCode: string;
  planName: string;
  status: string;
  periodFrom: string;
  periodTo: string;
  trialEndsAt: { value: string } | null;
  clients: UsageLimit;
  users: UsageLimit;
  documents: UsageLimit;
  aiProcessing: UsageLimit;
  storageBytes: UsageLimit;
  overLimit: boolean;
  writeAllowed: boolean;
}

export interface SubscriptionSummary {
  planCode: string;
  status: string;
  periodFrom: string | null;
  periodTo: string | null;
  trialEndsAt: string | null;
}

export interface PlanChangeRequest {
  id: string;
  currentPlanCode: string;
  requestedPlanCode: string;
  status: string;
  createdAt: string;
}
