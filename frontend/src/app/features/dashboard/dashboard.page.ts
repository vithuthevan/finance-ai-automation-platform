import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { money, monthStart, today } from '../reports/report-context.service';
import { DashboardSummary, PracticeDashboard } from '../reports/report.models';

@Component({
  standalone: true,
  imports: [FormsModule, MatCardModule, MatFormFieldModule, MatSelectModule],
  template: `
    <div class="page">
      <h1>{{ auth.hasRole('ADMIN', 'ACCOUNTANT') ? 'Practice dashboard' : 'Client dashboard' }}</h1>
      @if (auth.hasRole('ADMIN', 'ACCOUNTANT') && practice) {
        <p class="hint">Workload across assigned clients. Client finances are not summed together.</p>
        <div class="grid-2">
          <mat-card class="metric-card"><div class="label">Active clients</div><div class="value">{{ practice.activeClients }}</div></mat-card>
          <mat-card class="metric-card"><div class="label">Clients with drafts</div><div class="value">{{ practice.clientsWithDrafts }}</div></mat-card>
          <mat-card class="metric-card"><div class="label">Documents needing review</div><div class="value">{{ practice.documentsNeedingReview }}</div></mat-card>
          <mat-card class="metric-card"><div class="label">Pending approvals</div><div class="value">{{ practice.pendingApprovals }}</div></mat-card>
          <mat-card class="metric-card"><div class="label">Clients with recent activity</div><div class="value">{{ practice.clientsWithRecentActivity }}</div></mat-card>
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
  `
})
export class DashboardPage implements OnInit {
  practice: PracticeDashboard | null = null;
  clients: { id: string; name: string }[] = [];
  clientId = '';
  clientDash: DashboardSummary | null = null;

  constructor(private api: ApiService, readonly auth: AuthService) {}

  ngOnInit(): void {
    if (this.auth.hasRole('ADMIN', 'ACCOUNTANT')) {
      this.api.get<PracticeDashboard>('/api/v1/reports/practice').subscribe((practice) => this.practice = practice);
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
