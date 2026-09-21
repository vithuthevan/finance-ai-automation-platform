import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { MonthEndClientRow, MonthEndCommandCenter, MonthEndPortfolioState } from './month-end.models';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { stateBadgeStatus } from './close-action.util';

@Component({
  standalone: true,
  imports: [
    FormsModule, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    PageHeaderComponent, StatusBadgeComponent, LoadingStateComponent, EmptyStateComponent
  ],
  template: `
    <div class="page mecc">
      <app-page-header
        title="Month-end command center"
        [subtitle]="data?.periodLabel || 'Portfolio readiness for the current close period.'" />

      <div class="filter-bar">
        <mat-form-field appearance="outline">
          <mat-label>Search client</mat-label>
          <input matInput [(ngModel)]="query" (keyup.enter)="load()" placeholder="Business name" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Year</mat-label>
          <input matInput type="number" [(ngModel)]="year" (keyup.enter)="load()" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Month</mat-label>
          <mat-select [(ngModel)]="month" (selectionChange)="load()">
            @for (m of months; track m.value) {
              <mat-option [value]="m.value">{{ m.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="stateFilter" (selectionChange)="load()">
            <mat-option value="">All</mat-option>
            <mat-option value="READY">Ready</mat-option>
            <mat-option value="ATTENTION">Needs attention</mat-option>
            <mat-option value="BLOCKED">Blocked</mat-option>
            <mat-option value="CLOSED">Closed</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Focus</mat-label>
          <mat-select [(ngModel)]="focusFilter" (selectionChange)="load()">
            <mat-option value="">All</mat-option>
            <mat-option value="WAITING_ON_CLIENT">Waiting on client</mat-option>
            <mat-option value="TEAM_ACTION">Action required from team</mat-option>
          </mat-select>
        </mat-form-field>
        <button mat-stroked-button type="button" (click)="load()" [disabled]="loading">Refresh</button>
        <a mat-stroked-button routerLink="/app/work">Work queue</a>
      </div>

      @if (loading) {
        <app-loading-state message="Loading portfolio readiness…" />
      } @else if (!data) {
        <app-empty-state title="Could not load command center" description="Try refreshing or check your connection." icon="error" />
      } @else {
        <div class="summary-grid">
          <mat-card class="metric-card"><div class="label">Clients</div><div class="value">{{ data.summary.totalClients }}</div></mat-card>
          <mat-card class="metric-card ready"><div class="label">Ready</div><div class="value">{{ data.summary.ready }}</div></mat-card>
          <mat-card class="metric-card attention"><div class="label">Needs attention</div><div class="value">{{ data.summary.needsAttention }}</div></mat-card>
          <mat-card class="metric-card blocked"><div class="label">Blocked</div><div class="value">{{ data.summary.blocked }}</div></mat-card>
          <mat-card class="metric-card"><div class="label">Closed</div><div class="value">{{ data.summary.closed }}</div></mat-card>
          @if (data.summary.overdueDocumentRequests > 0) {
            <mat-card class="metric-card overdue link-card">
              <a routerLink="/app/requests" [queryParams]="{ overdueOnly: true }">
                <div class="label">Overdue requests</div>
                <div class="value">{{ data.summary.overdueDocumentRequests }}</div>
              </a>
            </mat-card>
          }
        </div>

        @if (!data.clients.length) {
          <app-empty-state
            title="No clients match"
            description="Create clients or adjust filters to see month-end readiness."
            icon="groups" />
        } @else {
          <div class="client-list">
            @for (row of data.clients; track row.clientId) {
              <mat-card class="client-card" [class]="'state-' + row.state.toLowerCase()">
                <div class="client-head">
                  <div>
                    <h3>{{ row.clientName }}</h3>
                    <div class="meta">
                      @if (row.primaryAccountantName) {
                        <span>{{ row.primaryAccountantName }}</span> ·
                      }
                      <app-status-badge [status]="badge(row.state)" [label]="row.state.replace('_', ' ')" />
                    </div>
                  </div>
                  <button mat-flat-button color="primary" type="button" (click)="openAction(row)">
                    {{ row.primaryAction.label }}
                  </button>
                </div>
                <div class="progress-row" aria-label="Workflow progress">
                  @for (step of row.progress; track step.code) {
                    <span class="progress-step" [title]="step.label">{{ step.label.split(' ')[0] }} {{ step.indicator }}</span>
                  }
                </div>
                @if (row.blockers.length) {
                  @if (clientBlockers(row).length) {
                    <div class="resp-group">
                      <div class="resp-label">Waiting on client</div>
                      <ul class="blockers">
                        @for (b of clientBlockers(row); track b.code + b.message) {
                          <li>
                            <span>{{ b.message }}</span>
                            <button mat-button type="button" (click)="openBlocker(b)">{{ b.action.label }}</button>
                          </li>
                        }
                      </ul>
                    </div>
                  }
                  @if (teamBlockers(row).length) {
                    <div class="resp-group">
                      <div class="resp-label">Your team</div>
                      <ul class="blockers">
                        @for (b of teamBlockers(row); track b.code + b.message) {
                          <li>
                            <span>{{ b.message }}</span>
                            <button mat-button type="button" (click)="openBlocker(b)">{{ b.action.label }}</button>
                          </li>
                        }
                      </ul>
                    </div>
                  }
                } @else if (row.state === 'READY') {
                  <p class="hint ok">All checks passed — review and close when satisfied.</p>
                }
              </mat-card>
            }
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .summary-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
      gap: 12px;
      margin-bottom: 20px;
    }
    .metric-card.ready { border-left: 4px solid #047857; }
    .metric-card.attention { border-left: 4px solid #d97706; }
    .metric-card.blocked { border-left: 4px solid #b91c1c; }
    .metric-card.overdue { border-left: 4px solid #7c2d12; }
    .link-card a { text-decoration: none; color: inherit; display: block; }
    .client-list { display: flex; flex-direction: column; gap: 14px; }
    .client-card { padding: 16px !important; }
    .client-head { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; flex-wrap: wrap; }
    .client-head h3 { margin: 0 0 4px; font-size: 17px; }
    .meta { font-size: 13px; color: var(--fp-muted); display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
    .progress-row { display: flex; flex-wrap: wrap; gap: 10px; margin: 12px 0; font-size: 12px; font-weight: 600; color: var(--fp-text-secondary); }
    .progress-step { padding: 4px 8px; background: var(--fp-background); border-radius: 6px; }
    .resp-group { margin-top: 8px; }
    .resp-label { font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; color: var(--fp-muted); margin-bottom: 4px; }
    .blockers { margin: 0; padding-left: 18px; }
    .blockers li { margin-bottom: 6px; display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
    .hint.ok { margin: 8px 0 0; color: #047857; font-weight: 600; }
    @media (max-width: 640px) {
      .client-head button { width: 100%; }
    }
  `]
})
export class MonthEndCommandCenterPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  data: MonthEndCommandCenter | null = null;
  loading = false;
  query = '';
  stateFilter = '';
  focusFilter = '';
  year = new Date().getFullYear();
  month = new Date().getMonth() + 1;
  months = [
    { value: 1, label: 'January' }, { value: 2, label: 'February' }, { value: 3, label: 'March' },
    { value: 4, label: 'April' }, { value: 5, label: 'May' }, { value: 6, label: 'June' },
    { value: 7, label: 'July' }, { value: 8, label: 'August' }, { value: 9, label: 'September' },
    { value: 10, label: 'October' }, { value: 11, label: 'November' }, { value: 12, label: 'December' }
  ];

  ngOnInit(): void {
    this.load();
  }

  clientBlockers(row: MonthEndClientRow) {
    return row.blockers.filter((b) => b.responsibility === 'CLIENT');
  }

  teamBlockers(row: MonthEndClientRow) {
    return row.blockers.filter((b) => b.responsibility === 'TEAM');
  }

  load(): void {
    const params: Record<string, string> = {
      year: String(this.year),
      month: String(this.month)
    };
    if (this.query.trim()) {
      params['query'] = this.query.trim();
    }
    if (this.stateFilter) {
      params['state'] = this.stateFilter;
    }
    if (this.focusFilter) {
      params['focus'] = this.focusFilter;
    }
    this.loading = true;
    this.api.get<MonthEndCommandCenter>('/api/v1/work/month-end-command-center', params).pipe(
      finalize(() => this.loading = false)
    ).subscribe({
      next: (data) => this.data = data,
      error: () => this.data = null
    });
  }

  badge(state: MonthEndPortfolioState): string {
    return stateBadgeStatus(state);
  }

  openAction(row: MonthEndClientRow): void {
    this.navigate(row.primaryAction);
  }

  openBlocker(b: { action: { path: string; query: Record<string, string> } }): void {
    this.navigate(b.action);
  }

  private navigate(action: { path: string; query: Record<string, string> }): void {
    const segments = action.path.replace(/^\/app\/?/, '').split('/').filter(Boolean);
    this.router.navigate(['/app', ...segments], { queryParams: action.query ?? {} });
  }
}
