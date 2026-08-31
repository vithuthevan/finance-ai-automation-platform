import { Injectable, signal } from '@angular/core';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';

@Injectable({ providedIn: 'root' })
export class ReportContextService {
  readonly clients = signal<any[]>([]);
  readonly clientId = signal('');
  readonly from = signal(monthStart());
  readonly to = signal(today());
  readonly compareFrom = signal('');
  readonly compareTo = signal('');
  readonly lockClient = signal(false);

  constructor(private api: ApiService, private auth: AuthService) {}

  loadClients(onReady?: () => void): void {
    this.api.list<any>('/api/v1/clients').subscribe((clients) => {
      this.clients.set(clients);
      this.lockClient.set(this.auth.hasRole('BUSINESS_OWNER') && clients.length <= 1);
      if (!this.clientId() && clients[0]) {
        this.clientId.set(clients[0].id);
      }
      onReady?.();
    });
  }

  params() {
    return { from: this.from(), to: this.to() };
  }
}

export function monthStart(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;
}

export function today(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

export function money(value: unknown): string {
  const number = Number(value ?? 0);
  if (Number.isNaN(number)) {
    return String(value ?? '');
  }
  return number.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
