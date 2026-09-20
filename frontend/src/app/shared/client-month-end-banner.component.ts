import { Component, OnInit, inject, effect } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/auth/auth.service';
import { ClientContextService } from '../core/services/client-context.service';
import { MonthEndClientRow, MonthEndCommandCenter } from '../features/month-end/month-end.models';
import { StatusBadgeComponent } from './ui/status-badge.component';
import { stateBadgeStatus } from '../features/month-end/close-action.util';

@Component({
  selector: 'app-client-month-end-banner',
  standalone: true,
  imports: [MatButtonModule, StatusBadgeComponent],
  template: `
    @if (row) {
      <div class="client-me-banner" role="status">
        <div>
          <strong>{{ row.clientName }}</strong>
          <span class="period">{{ data?.periodLabel }}</span>
          <app-status-badge [status]="badge(row.state)" [label]="row.state" />
          @if (row.blockers.length) {
            <span class="blocker-hint">{{ blockerSummary(row) }}</span>
          }
        </div>
        <button mat-stroked-button type="button" (click)="openPrimary(row)">{{ row.primaryAction.label }}</button>
      </div>
    }
  `,
  styles: [`
    .client-me-banner {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 12px 16px;
      margin-bottom: 16px;
      background: var(--fp-surface);
      border: 1px solid var(--fp-border);
      border-radius: var(--fp-radius-md);
    }
    .period { margin: 0 8px; color: var(--fp-muted); font-size: 13px; }
    .blocker-hint { margin-left: 8px; font-size: 13px; color: var(--fp-text-secondary); }
  `]
})
export class ClientMonthEndBannerComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly clientContext = inject(ClientContextService);
  private readonly router = inject(Router);

  data: MonthEndCommandCenter | null = null;
  row: MonthEndClientRow | null = null;

  constructor() {
    effect(() => {
      this.clientContext.clientId();
      this.load();
    });
  }

  ngOnInit(): void {
    if (!this.auth.hasRole('ADMIN', 'ACCOUNTANT')) {
      return;
    }
  }

  load(): void {
    const clientId = this.clientContext.clientId();
    if (!clientId) {
      this.row = null;
      return;
    }
    this.api.get<MonthEndCommandCenter>('/api/v1/work/month-end-command-center', { clientId }).subscribe({
      next: (data) => {
        this.data = data;
        this.row = data.clients[0] ?? null;
      },
      error: () => {
        this.data = null;
        this.row = null;
      }
    });
  }

  badge(state: string): string {
    return stateBadgeStatus(state);
  }

  blockerSummary(row: MonthEndClientRow): string {
    const blockers = row.blockers.filter((b) => b.severity === 'BLOCKER');
    if (!blockers.length) {
      return row.blockers.map((b) => b.message).slice(0, 2).join(' · ');
    }
    return blockers.map((b) => b.message).slice(0, 3).join(' · ');
  }

  openPrimary(row: MonthEndClientRow): void {
    const segments = row.primaryAction.path.replace(/^\/app\/?/, '').split('/').filter(Boolean);
    this.router.navigate(['/app', ...segments], { queryParams: row.primaryAction.query ?? {} });
  }
}
