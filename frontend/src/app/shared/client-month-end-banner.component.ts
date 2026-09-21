import { Component, OnInit, inject, effect } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/auth/auth.service';
import { ClientContextService } from '../core/services/client-context.service';
import { MonthEndBlocker, MonthEndClientRow, MonthEndCommandCenter } from '../features/month-end/month-end.models';
import { StatusBadgeComponent } from './ui/status-badge.component';
import { stateBadgeStatus } from '../features/month-end/close-action.util';

@Component({
  selector: 'app-client-month-end-banner',
  standalone: true,
  imports: [MatButtonModule, StatusBadgeComponent],
  template: `
    @if (row) {
      <div class="client-me-banner" role="status">
        <div class="client-me-banner__main">
          <strong>{{ row.clientName }}</strong>
          <span class="period">{{ data?.periodLabel }}</span>
          <app-status-badge [status]="badge(row.state)" [label]="row.state.replace('_', ' ')" />
          @if (clientBlockers(row).length) {
            <div class="resp">
              <span class="resp-label">Waiting on client</span>
              @for (b of clientBlockers(row); track b.code) {
                <span class="resp-item">{{ b.message }}</span>
              }
            </div>
          }
          @if (teamBlockers(row).length) {
            <div class="resp">
              <span class="resp-label">Your team</span>
              @for (b of teamBlockers(row); track b.code) {
                <span class="resp-item">{{ b.message }}</span>
              }
            </div>
          }
        </div>
        <div class="client-me-banner__actions">
          @for (b of row.blockers.slice(0, 2); track b.code) {
            <button mat-button type="button" (click)="openBlocker(b)">{{ b.action.label }}</button>
          }
          <button mat-stroked-button type="button" (click)="openPrimary(row)">{{ row.primaryAction.label }}</button>
        </div>
      </div>
    }
  `,
  styles: [`
    .client-me-banner {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
      padding: 12px 16px;
      margin-bottom: 16px;
      background: var(--fp-surface);
      border: 1px solid var(--fp-border);
      border-radius: var(--fp-radius-md);
    }
    .client-me-banner__main { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; font-size: 13px; }
    .client-me-banner__actions { display: flex; flex-wrap: wrap; gap: 4px; align-items: center; }
    .period { color: var(--fp-muted); }
    .resp { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
    .resp-label { font-weight: 600; font-size: 12px; text-transform: uppercase; color: var(--fp-text-muted); }
    .resp-item { color: var(--fp-text-secondary); }
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

  clientBlockers(row: MonthEndClientRow): MonthEndBlocker[] {
    return row.blockers.filter((b) => b.responsibility === 'CLIENT');
  }

  teamBlockers(row: MonthEndClientRow): MonthEndBlocker[] {
    return row.blockers.filter((b) => b.responsibility === 'TEAM');
  }

  openBlocker(blocker: MonthEndBlocker): void {
    const segments = blocker.action.path.replace(/^\/app\/?/, '').split('/').filter(Boolean);
    this.router.navigate(['/app', ...segments], { queryParams: blocker.action.query ?? {} });
  }

  openPrimary(row: MonthEndClientRow): void {
    const segments = row.primaryAction.path.replace(/^\/app\/?/, '').split('/').filter(Boolean);
    this.router.navigate(['/app', ...segments], { queryParams: row.primaryAction.query ?? {} });
  }
}
