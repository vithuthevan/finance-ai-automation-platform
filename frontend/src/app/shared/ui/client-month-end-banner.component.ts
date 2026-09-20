import { Component, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ClientContextService } from '../../core/services/client-context.service';
import { MonthEndCommandCenter } from '../../features/month-end/month-end.models';
import { StatusBadgeComponent } from './status-badge.component';

@Component({
  selector: 'app-client-month-end-banner',
  standalone: true,
  imports: [RouterLink, MatButtonModule, StatusBadgeComponent],
  template: `
    @if (row()) {
      <div class="client-me-banner" role="status">
        <div class="client-me-banner__main">
          <span class="client-me-banner__period">{{ periodLabel() }}</span>
          <app-status-badge [status]="badge(row()!.state)" [label]="row()!.state" />
          @if (row()!.blockers.length) {
            <span class="client-me-banner__blockers">{{ blockerSummary(row()!) }}</span>
          }
        </div>
        <a mat-stroked-button color="primary" [routerLink]="actionLink()">{{ row()!.primaryAction.label }}</a>
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
    .client-me-banner__main { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; font-size: 13px; }
    .client-me-banner__period { font-weight: 700; color: var(--fp-text-primary); }
    .client-me-banner__blockers { color: var(--fp-text-muted); }
  `]
})
export class ClientMonthEndBannerComponent {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly clientContext = inject(ClientContextService);

  row = signal<MonthEndCommandCenter['clients'][0] | null>(null);

  constructor() {
    effect(() => {
      const clientId = this.clientContext.clientId();
      if (!this.auth.hasRole('ADMIN', 'ACCOUNTANT') || !clientId) {
        this.row.set(null);
        return;
      }
      this.api.get<MonthEndCommandCenter>('/api/v1/work/month-end-command-center', { clientId }).subscribe({
        next: (cc) => this.row.set(cc.clients[0] ?? null),
        error: () => this.row.set(null)
      });
    });
  }

  periodLabel(): string {
    const r = this.row();
    if (!r) {
      return '';
    }
    return `${r.year}-${String(r.month).padStart(2, '0')}`;
  }

  actionLink(): string[] {
    const path = this.row()?.primaryAction.path ?? '/app/month-end';
    const segments = path.replace(/^\/app\/?/, '').split('/').filter(Boolean);
    return ['/app', ...segments];
  }

  badge(state: string): string {
    switch (state) {
      case 'READY': return 'APPROVED';
      case 'ATTENTION': return 'PENDING';
      case 'BLOCKED': return 'VOID';
      case 'CLOSED': return 'CLOSED';
      default: return state;
    }
  }

  blockerSummary(row: MonthEndCommandCenter['clients'][0]): string {
    const blockers = row.blockers.filter((b) => b.severity === 'BLOCKER');
    if (!blockers.length) {
      return row.state === 'ATTENTION' ? 'Review warnings before close' : '';
    }
    return blockers.map((b) => b.message).slice(0, 2).join(' · ');
  }
}
