import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { money, monthStart, today } from '../reports/report-context.service';
import { DashboardSummary, PracticeDashboard } from '../reports/report.models';
import { WorkSummary } from '../work/work.models';
import { SubscriptionUsage } from '../admin/subscription.models';

@Component({
  standalone: true,
  imports: [FormsModule, MatCardModule, MatFormFieldModule, MatSelectModule, MatIconModule, MatButtonModule, RouterLink],
  template: `
    <div class="page dash">
      <div class="dash-head">
        <div>
          <h1>{{ auth.hasRole('ADMIN', 'ACCOUNTANT') ? 'Dashboard' : 'Client dashboard' }}</h1>
          <p class="page-subtitle">Welcome back — here’s a snapshot of your practice today.</p>
        </div>
        <div class="dash-actions">
          @if (auth.hasRole('ADMIN', 'ACCOUNTANT')) {
            <a mat-stroked-button color="primary" routerLink="/app/work">Open work queue</a>
            <a mat-stroked-button routerLink="/app/reports">Report summary</a>
          }
          <div class="date-chip">{{ todayLabel }}</div>
        </div>
      </div>

      @if (auth.hasRole('ADMIN') && usageWarnings.length) {
        <div class="subscription-banner warning cardish">
          Plan usage: {{ usageWarnings.join(' · ') }} — <a routerLink="/app/subscription">View subscription</a>
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

        <div class="panel">
          <div class="panel-head">
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
        </div>
      }

      @if (auth.hasRole('ADMIN', 'ACCOUNTANT') && practice) {
        <div class="panel">
          <div class="panel-head">
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

      <div class="panel">
        <div class="panel-head">
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
        }
      </div>
    </div>
  `,
  styles: [`
    .dash-head {
      display: flex;
      justify-content: space-between;
      gap: 16px;
      align-items: flex-start;
      margin-bottom: 8px;
      flex-wrap: wrap;
    }

    .dash-actions {
      display: flex;
      gap: 10px;
      align-items: center;
      flex-wrap: wrap;
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

    .panel {
      background: #fff;
      border: 1px solid var(--fp-line);
      border-radius: 14px;
      padding: 18px;
      margin-bottom: 16px;
      box-shadow: var(--fp-shadow);
    }

    .panel-head {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      align-items: flex-start;
      flex-wrap: wrap;
      margin-bottom: 8px;
    }

    .panel h2 {
      margin: 0 0 4px;
    }

    .panel .hint {
      margin: 0 0 12px;
    }

    .client-select {
      width: min(280px, 100%);
    }

    .link-card a {
      text-decoration: none;
      color: inherit;
      display: block;
    }

    .cardish {
      border-radius: 12px;
      margin-bottom: 14px;
      border: 1px solid #ffd7b0;
    }

    @media (max-width: 800px) {
      .hero-grid { grid-template-columns: 1fr; }
    }
  `]
})
export class DashboardPage implements OnInit {
  practice: PracticeDashboard | null = null;
  workSummary: WorkSummary | null = null;
  clients: { id: string; name: string }[] = [];
  clientId = '';
  clientDash: DashboardSummary | null = null;
  usageWarnings: string[] = [];
  todayLabel = new Date().toLocaleDateString();

  constructor(private api: ApiService, readonly auth: AuthService) {}

  ngOnInit(): void {
    if (this.auth.hasRole('ADMIN')) {
      this.api.get<SubscriptionUsage>('/api/v1/subscription/usage').subscribe((usage) => {
        this.usageWarnings = [];
        if (usage.documents.percentUsed >= 80) this.usageWarnings.push(`Documents ${usage.documents.percentUsed}%`);
        if (usage.aiProcessing.percentUsed >= 80) this.usageWarnings.push(`AI ${usage.aiProcessing.percentUsed}%`);
        if (usage.storageBytes.percentUsed >= 80) this.usageWarnings.push(`Storage ${usage.storageBytes.percentUsed}%`);
      });
    }
    if (this.auth.hasRole('ADMIN', 'ACCOUNTANT')) {
      this.api.get<PracticeDashboard>('/api/v1/reports/practice').subscribe((practice) => this.practice = practice);
      this.api.get<WorkSummary>('/api/v1/work/summary').subscribe((summary) => this.workSummary = summary);
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
      next: (dashboard) => this.clientDash = dashboard,
      error: () => this.clientDash = null
    });
  }

  format(value: unknown): string {
    return money(value);
  }
}
