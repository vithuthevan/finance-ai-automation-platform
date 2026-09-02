import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { money, monthStart, today } from '../reports/report-context.service';
import { DashboardSummary, PracticeDashboard } from '../reports/report.models';
import { WorkSummary } from '../work/work.models';
import { SubscriptionUsage } from '../admin/subscription.models';

@Component({
  standalone: true,
  imports: [FormsModule, MatCardModule, MatFormFieldModule, MatSelectModule, RouterLink],
  template: `
    <div class="page">
      <h1>{{ auth.hasRole('ADMIN', 'ACCOUNTANT') ? 'Practice dashboard' : 'Client dashboard' }}</h1>
      @if (auth.hasRole('ADMIN') && usageWarnings.length) {
        <div class="subscription-banner warning">
          Plan usage: {{ usageWarnings.join(' · ') }} — <a routerLink="/app/subscription">View subscription</a>
        </div>
      }
      @if (auth.hasRole('ADMIN', 'ACCOUNTANT') && workSummary) {
        <p class="hint">Actionable workflow for today. Every card links to the relevant queue.</p>
        <h2>Today</h2>
        <div class="grid-2">
          <mat-card class="metric-card link-card"><a routerLink="/app/work" [queryParams]="{type:'DOCUMENT_REVIEW'}"><div class="label">Documents to review</div><div class="value">{{ workSummary.documentsToReview }}</div></a></mat-card>
          <mat-card class="metric-card link-card"><a routerLink="/app/work" [queryParams]="{type:'TRANSACTION_APPROVAL'}"><div class="label">Approvals pending</div><div class="value">{{ workSummary.pendingApprovals }}</div></a></mat-card>
          <mat-card class="metric-card link-card"><a routerLink="/app/work" [queryParams]="{type:'BANK_RECONCILIATION'}"><div class="label">Bank items unresolved</div><div class="value">{{ workSummary.bankItemsUnresolved }}</div></a></mat-card>
          <mat-card class="metric-card link-card"><a routerLink="/app/work" [queryParams]="{type:'DOCUMENT_REQUEST',overdueOnly:true}"><div class="label">Client requests overdue</div><div class="value">{{ workSummary.overdueDocumentRequests }}</div></a></mat-card>
          <mat-card class="metric-card link-card"><a routerLink="/app/work" [queryParams]="{type:'PERIOD_CLOSE'}"><div class="label">Clients ready to close</div><div class="value">{{ workSummary.clientsReadyToClose }}</div></a></mat-card>
        </div>
      }
      @if (auth.hasRole('ADMIN', 'ACCOUNTANT') && practice) {
        <h2>Practice overview</h2>
        <div class="grid-2">
          <mat-card class="metric-card"><div class="label">Active clients</div><div class="value">{{ practice.activeClients }}</div></mat-card>
          <mat-card class="metric-card"><div class="label">Clients with drafts</div><div class="value">{{ practice.clientsWithDrafts }}</div></mat-card>
          <mat-card class="metric-card"><div class="label">Documents needing review</div><div class="value">{{ practice.documentsNeedingReview }}</div></mat-card>
          <mat-card class="metric-card"><div class="label">Pending approvals</div><div class="value">{{ practice.pendingApprovals }}</div></mat-card>
        </div>
      }
      <h2>Selected client this month</h2>
      <div class="toolbar-row">
        <mat-form-field>
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
  `,
  styles: ['.link-card a { text-decoration: none; color: inherit; display: block; }']
})
export class DashboardPage implements OnInit {
  practice: PracticeDashboard | null = null;
  workSummary: WorkSummary | null = null;
  clients: { id: string; name: string }[] = [];
  clientId = '';
  clientDash: DashboardSummary | null = null;
  usageWarnings: string[] = [];

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
