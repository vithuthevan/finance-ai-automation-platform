export interface NotificationItem {
  id: string;
  clientId?: string | null;
  type: string;
  title: string;
  message: string;
  resourceType?: string | null;
  resourceId?: string | null;
  actionUrl?: string | null;
  read: boolean;
  readAt?: string | null;
  createdAt: string;
}

export interface UnreadCount {
  count: number;
}
