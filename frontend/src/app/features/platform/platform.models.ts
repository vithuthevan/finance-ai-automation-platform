import { SubscriptionUsage } from '../admin/subscription.models';

export interface PlatformFirm {
  id: string;
  name: string;
  planCode: string | null;
  status: string | null;
}

export interface PlatformFirmDetail extends PlatformFirm {
  usage: SubscriptionUsage;
}

export interface PlatformMetrics {
  totalFirms: number;
  activeSubscriptions: number;
  trialFirms: number;
  suspendedFirms: number;
}
