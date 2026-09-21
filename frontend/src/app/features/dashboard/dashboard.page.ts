import { Component, OnInit, ViewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { money, monthStart, today } from '../reports/report-context.service';
import { DashboardSummary, PracticeDashboard } from '../reports/report.models';
import { MonthEndCommandCenter, OnboardingChecklist } from '../month-end/month-end.models';
import { WorkSummary } from '../work/work.models';
import { SubscriptionUsage } from '../admin/subscription.models';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';

@Component({
  standalone: true,
  imports: [
    FormsModule, MatCardModule, MatFormFieldModule, MatSelectModule, MatIconModule,
    MatButtonModule, RouterLink, BaseChartDirective, PageHeaderComponent
  ],
  template: `
    <div class="page dash">
      <app-page-header
        [title]="auth.hasRole('ADMIN', 'ACCOUNTANT') ? 'Dashboard' : 'Client dashboard'"
        subtitle="Welcome back — here’s a snapshot of your practice today.">
        <div fpPageActions class="toolbar-row dash-actions">
        @if (auth.hasRole('ADMIN', 'ACCOUNTANT')) {
          <a mat-flat-button color="primary" routerLink="/app/month-end">Month-end command center</a>
          <a mat-stroked-button routerLink="/app/work">Open work queue</a>
          <a mat-stroked-button routerLink="/app/reports">Report summary</a>
        }
        <div class="date-chip">{{ todayLabel }}</div>
        </div>
      </app-page-header>

      @if (auth.hasRole('ADMIN', 'ACCOUNTANT') && commandCenter) {
        <div class="card-block mecc-teaser">
          <div class="toolbar-row">
            <div>
              <h2>{{ commandCenter.periodLabel }}</h2>
              <p class="hint">Ready {{ commandCenter.summary.ready }} · Blocked {{ commandCenter.summary.blocked }} · Attention {{ commandCenter.summary.needsAttention }}</p>
            </div>
            <a mat-stroked-button routerLink="/app/month-end">View command center</a>
          </div>
        </div>
      }

      @if (auth.hasRole('ADMIN') && onboarding && onboarding.completedCount < onboarding.totalCount) {
        <div class="card-block onboarding">
          <h2>Welcome{{ onboarding.firmName ? (' to ' + onboarding.firmName) : '' }}</h2>
          <p class="hint">Let's prepare your practice for the first client close.</p>
          <ul class="onboard-steps">
            @for (step of onboarding.steps; track step.code) {
              <li [class.done]="step.completed">
                <span>{{ step.completed ? '✓' : '○' }}</span>
                <a [routerLink]="step.actionPath">{{ step.label }}</a>
              </li>
            }
          </ul>
          <p class="hint">{{ onboarding.completedCount }} / {{ onboarding.totalCount }} complete</p>
        </div>
      }

      @if (auth.hasRole('ADMIN', 'ACCOUNTANT') && practiceToday) {
        <div class="card-block mecc-teaser">
          <div class="toolbar-row">
            <div>
              <h2>Today</h2>
              <p class="hint">
                {{ practiceToday.clientsNeedAttention }} need attention ·
                {{ practiceToday.readyToClose }} ready to close ·
                {{ practiceToday.blockedByMissingDocuments }} blocked by documents ·
                {{ practiceToday.unreconciledTransactions }} unreconciled ·
                {{ practiceToday.overdueInvoices }} overdue invoices
              </p>
            </div>
            <a mat-stroked-button routerLink="/app/work">What should I work on next?</a>
          </div>
        </div>
      }

      @if (auth.hasRole('ADMIN', 'ACCOUNTANT') && workSummary) {
        <div class="hero-grid">
          <mat-card class="metric-card hero-card">
            <div class="hero-icon"><mat-icon>rate_review</mat-icon></div>
            <div class="label">Documents to review</div>
            <div class="value">{{ workSummary.documentsToReview }}</div>
            <div class="meta">Needs accountant attention</div>
            <a class="card-link" routerLink="/app/work" [queryParams]="{type:'DOCUMENT_REVIEW'}">Open queue</a>
          </mat-card>
          <mat-card class="metric-card hero-card">
            <div class="hero-icon"><mat-icon>approval</mat-icon></div>
            <div class="label">Approvals pending</div>
            <div class="value">{{ workSummary.pendingApprovals }}</div>
            <div class="meta">Draft ledger items waiting</div>
            <a class="card-link" routerLink="/app/work" [queryParams]="{type:'TRANSACTION_APPROVAL'}">Review approvals</a>
          </mat-card>
        </div>

        <div class="card-block">
          <div class="toolbar-row">
            <div>
              <h2>Today’s workflow</h2>
              <p class="hint">Every card links to the relevant queue.</p>
            </div>
          </div>
          <div class="grid-2">
            <mat-card class="metric-card link-card">
              <a routerLink="/app/work" [queryParams]="{type:'BANK_RECONCILIATION'}">
                <div class="label">Bank items unresolved</div>
                <div class="value">{{ workSummary.bankItemsUnresolved }}</div>
              </a>
            </mat-card>
            <mat-card class="metric-card link-card">
              <a routerLink="/app/work" [queryParams]="{type:'DOCUMENT_REQUEST',overdueOnly:true}">
                <div class="label">Client requests overdue</div>
                <div class="value">{{ workSummary.overdueDocumentRequests }}</div>
              </a>
            </mat-card>
            <mat-card class="metric-card link-card">
              <a routerLink="/app/work" [queryParams]="{type:'PERIOD_CLOSE'}">
                <div class="label">Clients ready to close</div>
                <div class="value">{{ workSummary.clientsReadyToClose }}</div>
              </a>
            </mat-card>
          </div>
          <div class="chart-panel chart-wrap">
            <canvas baseChart [data]="workChartData" [options]="barOptions" [type]="'bar'"></canvas>
          </div>
        </div>
      }

      @if (auth.hasRole('ADMIN') && usage) {
        <div class="card-block">
          <div class="toolbar-row">
            <div>
              <h2>Plan usage</h2>
              <p class="hint">Percent of subscription limits used this period.</p>
            </div>
          </div>
          <div class="chart-panel chart-wrap">
            <canvas baseChart [data]="usageChartData" [options]="barOptions" [type]="'bar'"></canvas>
          </div>
        </div>
      }

      @if (auth.hasRole('ADMIN', 'ACCOUNTANT') && practice) {
        <div class="card-block">
          <div class="toolbar-row">
            <div>
              <h2>Practice overview</h2>
              <p class="hint">Firm-wide pipeline health.</p>
            </div>
          </div>
          <div class="grid-2">
            <mat-card class="metric-card"><div class="label">Active clients</div><div class="value">{{ practice.activeClients }}</div></mat-card>
            <mat-card class="metric-card"><div class="label">Clients with drafts</div><div class="value">{{ practice.clientsWithDrafts }}</div></mat-card>
            <mat-card class="metric-card"><div class="label">Documents needing review</div><div class="value">{{ practice.documentsNeedingReview }}</div></mat-card>
            <mat-card class="metric-card"><div class="label">Pending approvals</div><div class="value">{{ practice.pendingApprovals }}</div></mat-card>
          </div>
        </div>
      }

      <div class="card-block">
        <div class="toolbar-row">
          <div>
            <h2>Selected client this month</h2>
            <p class="hint">Approved activity for the chosen client.</p>
          </div>
          <mat-form-field appearance="outline" class="client-select">
            <mat-label>Client</mat-label>
            <mat-select [(ngModel)]="clientId" (selectionChange)="loadClient()">
              @for (client of clients; track client.id) {
                <mat-option [value]="client.id">{{ client.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>

        @if (clientDash && !clientDash.hasApprovedData) {
          <p class="empty-report">No approved activity this month for the selected client.</p>
        }
        @if (clientDash) {
          <div class="grid-2">
            <mat-card class="metric-card"><div class="label">Income</div><div class="value">{{ format(clientDash.income) }} {{ clientDash.currencyCode }}</div></mat-card>
            <mat-card class="metric-card"><div class="label">Expenses</div><div class="value">{{ format(clientDash.expenses) }} {{ clientDash.currencyCode }}</div></mat-card>
            <mat-card class="metric-card"><div class="label">{{ clientDash.resultType }}</div><div class="value">{{ format(clientDash.profitOrLoss) }} {{ clientDash.currencyCode }}</div></mat-card>
            <mat-card class="metric-card"><div class="label">Draft transactions</div><div class="value">{{ clientDash.unapprovedTransactions }}</div></mat-card>
            <mat-card class="metric-card"><div class="label">Unlinked documents</div><div class="value">{{ clientDash.unlinkedDocuments }}</div></mat-card>
            <mat-card class="metric-card"><div class="label">Documents needing review</div><div class="value">{{ clientDash.unreviewedDocuments }}</div></mat-card>
          </div>
          @if (statusChartData.datasets[0]?.data?.length) {
            <div class="chart-panel chart-wrap">
              <canvas baseChart [data]="statusChartData" [options]="doughnutOptions" [type]="'doughnut'"></canvas>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .dash-actions {
      align-items: center;
      margin-top: -10px;
    }

    .dash-actions .date-chip {
      margin-left: auto;
    }

    .date-chip {
      padding: 8px 12px;
      border-radius: 10px;
      border: 1px solid var(--fp-line);
      background: #fff;
      color: var(--fp-muted);
      font-size: 13px;
      font-weight: 600;
    }

    .hero-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 14px;
      margin: 8px 0 18px;
    }

    .hero-card {
      position: relative;
      padding-top: 20px !important;
    }

    .hero-icon {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      display: grid;
      place-items: center;
      background: var(--fp-orange-soft);
      color: var(--fp-orange);
      margin-bottom: 10px;
    }

    .hero-icon mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }

    .card-link {
      display: inline-block;
      margin-top: 12px;
      color: var(--fp-orange);
      font-weight: 600;
      font-size: 13px;
      text-decoration: none;
    }

    .client-select {
      width: min(280px, 100%);
    }

    .link-card a {
      text-decoration: none;
      color: inherit;
      display: block;
    }

    .chart-wrap {
      height: 260px;
      margin-top: 12px;
    }

    .onboard-steps { list-style: none; padding: 0; margin: 12px 0; }
    .onboard-steps li { display: flex; gap: 8px; margin-bottom: 8px; font-size: 14px; }
    .onboard-steps li.done { color: #047857; }
    .onboard-steps a { color: var(--fp-orange); font-weight: 600; text-decoration: none; }
    .mecc-teaser { border-left: 4px solid var(--fp-orange); }

    @media (max-width: 800px) {
      .hero-grid { grid-template-columns: 1fr; }
    }

    @media (max-width: 640px) {
      .dash-actions {
        flex-direction: column;
        align-items: stretch;
      }

      .dash-actions .date-chip {
        margin-left: 0;
        text-align: center;
      }

      .dash-actions > a.mat-mdc-button-base {
        width: 100%;
        justify-content: center;
      }
    }
  `]
})
export class DashboardPage implements OnInit {
  @ViewChild(BaseChartDirective) chart?: BaseChartDirective;

  practice: PracticeDashboard | null = null;
  workSummary: WorkSummary | null = null;
  practiceToday: {
    clientsNeedAttention: number;
    readyToClose: number;
    blockedByMissingDocuments: number;
    unreconciledTransactions: number;
    aiReviewsPending: number;
    overdueInvoices: number;
  } | null = null;
  commandCenter: MonthEndCommandCenter | null = null;
  onboarding: OnboardingChecklist | null = null;
  clients: { id: string; name: string }[] = [];
  clientId = '';
  clientDash: DashboardSummary | null = null;
  usage: SubscriptionUsage | null = null;
  todayLabel = new Date().toLocaleDateString();

  workChartData: ChartData<'bar'> = { labels: [], datasets: [] };
  usageChartData: ChartData<'bar'> = { labels: [], datasets: [] };
  statusChartData: ChartData<'doughnut'> = { labels: [], datasets: [] };

  barOptions: ChartConfiguration<'bar'>['options'] = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      x: { beginAtZero: true, grid: { color: '#e8ecf1' } },
      y: { grid: { display: false } }
    }
  };

  doughnutOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom' } }
  };

  constructor(private api: ApiService, readonly auth: AuthService) {}

  ngOnInit(): void {
    if (this.auth.hasRole('ADMIN')) {
      this.api.get<SubscriptionUsage>('/api/v1/subscription/usage').subscribe((usage) => {
        this.usage = usage;
        this.usageChartData = {
          labels: ['Documents', 'AI processing', 'Storage'],
          datasets: [{
            data: [usage.documents.percentUsed, usage.aiProcessing.percentUsed, usage.storageBytes.percentUsed],
            backgroundColor: ['#e87722', '#5c6472', '#0f766e'],
            borderRadius: 4
          }]
        };
      });
      this.api.get<OnboardingChecklist>('/api/v1/work/onboarding-checklist').subscribe({
        next: (ob) => this.onboarding = ob,
        error: () => this.onboarding = null
      });
    }
    if (this.auth.hasRole('ADMIN', 'ACCOUNTANT')) {
      this.api.get<PracticeDashboard>('/api/v1/reports/practice').subscribe((practice) => this.practice = practice);
      this.api.get<MonthEndCommandCenter>('/api/v1/work/month-end-command-center').subscribe({
        next: (cc) => this.commandCenter = cc,
        error: () => this.commandCenter = null
      });
      this.api.get<typeof this.practiceToday>('/api/v1/practice/today').subscribe({
        next: (today) => this.practiceToday = today,
        error: () => this.practiceToday = null
      });
      this.api.get<WorkSummary>('/api/v1/work/summary').subscribe((summary) => {
        this.workSummary = summary;
        this.workChartData = {
          labels: ['Docs to review', 'Approvals', 'Bank unresolved', 'Overdue requests', 'Ready to close'],
          datasets: [{
            data: [
              summary.documentsToReview,
              summary.pendingApprovals,
              summary.bankItemsUnresolved,
              summary.overdueDocumentRequests,
              summary.clientsReadyToClose
            ],
            backgroundColor: '#e87722',
            borderRadius: 4
          }]
        };
      });
    }
    this.api.list<{ id: string; name: string }>('/api/v1/clients').subscribe((clients) => {
      this.clients = clients;
      this.clientId = clients[0]?.id ?? '';
      this.loadClient();
    });
  }

  loadClient(): void {
    if (!this.clientId) {
      return;
    }
    this.api.get<DashboardSummary>(`/api/v1/clients/${this.clientId}/reports/dashboard`, {
      from: monthStart(),
      to: today()
    }).subscribe({
      next: (dashboard) => {
        this.clientDash = dashboard;
        const s = dashboard.statusSummary;
        if (!s) {
          this.statusChartData = { labels: [], datasets: [] };
          return;
        }
        this.statusChartData = {
          labels: ['Draft exp', 'Approved exp', 'Void exp', 'Draft inc', 'Approved inc', 'Void inc'],
          datasets: [{
            data: [
              s.draftExpenses, s.approvedExpenses, s.voidExpenses,
              s.draftIncome, s.approvedIncome, s.voidIncome
            ],
            backgroundColor: ['#f59e0b', '#047857', '#9b1c1c', '#fb923c', '#0f766e', '#b91c1c']
          }]
        };
      },
      error: () => this.clientDash = null
    });
  }

  format(value: unknown): string {
    return money(value);
  }
}
