import { Component, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { ApiService } from '../../core/services/api.service';
import { WorkItem, WorkSummary, ClientPortfolioItem } from './work.models';

@Component({
  standalone: true,
  imports: [RouterLink, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatSelectModule],
  template: `
    <div class="page">
      <h1>My work</h1>
      @if (summary) {
        <div class="grid-2">
          <mat-card class="metric-card link-card"><a routerLink="/app/work" [queryParams]="{type:'DOCUMENT_REVIEW'}"><div class="label">Documents to review</div><div class="value">{{ summary.documentsToReview }}</div></a></mat-card>
          <mat-card class="metric-card link-card"><a routerLink="/app/work" [queryParams]="{type:'TRANSACTION_APPROVAL'}"><div class="label">Approvals pending</div><div class="value">{{ summary.pendingApprovals }}</div></a></mat-card>
          <mat-card class="metric-card link-card"><a routerLink="/app/work" [queryParams]="{type:'BANK_RECONCILIATION'}"><div class="label">Bank items unresolved</div><div class="value">{{ summary.bankItemsUnresolved }}</div></a></mat-card>
          <mat-card class="metric-card link-card"><a routerLink="/app/work" [queryParams]="{type:'DOCUMENT_REQUEST',overdueOnly:true}"><div class="label">Overdue requests</div><div class="value">{{ summary.overdueDocumentRequests }}</div></a></mat-card>
          <mat-card class="metric-card link-card"><a routerLink="/app/work" [queryParams]="{type:'PERIOD_CLOSE'}"><div class="label">Clients ready to close</div><div class="value">{{ summary.clientsReadyToClose }}</div></a></mat-card>
        </div>
      }
      @if (portfolio.length) {
        <h2>Client portfolio</h2>
        <table class="data-table">
          <thead><tr><th>Client</th><th>Accountant</th><th>Review</th><th>Bank</th><th>Close</th></tr></thead>
          <tbody>
            @for (row of portfolio; track row.clientId) {
              <tr>
                <td>{{ row.clientName }}</td>
                <td>{{ row.primaryAccountantName || '—' }}</td>
                <td>{{ row.documentsNeedingReview === 0 ? 'Clear' : row.documentsNeedingReview + ' review' }}</td>
                <td>{{ row.bankReconciliationPercent == null ? '—' : row.bankReconciliationPercent + '%' }}</td>
                <td>{{ row.readyToClose ? 'Ready' : row.closeStatus }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
      <div class="toolbar-row">
        <mat-form-field>
          <mat-label>Type</mat-label>
          <mat-select [(ngModel)]="type" (selectionChange)="load()">
            <mat-option value="">All</mat-option>
            <mat-option value="DOCUMENT_REVIEW">Document review</mat-option>
            <mat-option value="DOCUMENT_PROCESSING_FAILURE">Processing failures</mat-option>
            <mat-option value="TRANSACTION_APPROVAL">Approvals</mat-option>
            <mat-option value="DOCUMENT_REQUEST">Document requests</mat-option>
            <mat-option value="BANK_RECONCILIATION">Bank reconciliation</mat-option>
            <mat-option value="PERIOD_CLOSE">Period close</mat-option>
          </mat-select>
        </mat-form-field>
      </div>
      <table class="data-table">
        <thead><tr><th>Priority</th><th>Client</th><th>Type</th><th>Description</th><th>Due</th><th></th></tr></thead>
        <tbody>
          @for (item of items; track item.resourceId + item.type) {
            <tr [class.overdue]="item.overdue">
              <td>{{ item.priority }}</td>
              <td>{{ item.clientName }}</td>
              <td>{{ item.type }}</td>
              <td>{{ item.title }}</td>
              <td>{{ item.dueDate || '—' }}</td>
              <td><a href="#" (click)="$event.preventDefault(); openItem(item)">Open</a></td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: [`
    .link-card a { text-decoration: none; color: inherit; display: block; }
    .overdue { background: #fff4f4; }
  `]
})
export class WorkPage implements OnInit {
  summary: WorkSummary | null = null;
  portfolio: ClientPortfolioItem[] = [];
  items: WorkItem[] = [];
  type = '';

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit(): void {
    this.api.get<WorkSummary>('/api/v1/work/summary').subscribe((summary) => this.summary = summary);
    this.api.get<ClientPortfolioItem[]>('/api/v1/work/portfolio').subscribe((portfolio) => this.portfolio = portfolio);
    this.load();
  }

  load(): void {
    const params: Record<string, string | number | boolean> = { page: 0, size: 50 };
    if (this.type) {
      params['type'] = this.type;
    }
    this.api.get<{ content: WorkItem[] }>('/api/v1/work/my', params).subscribe({
      next: (response) => this.items = response.content ?? []
    });
  }

  openItem(item: WorkItem): void {
    if (item.actionUrl) {
      this.router.navigateByUrl(item.actionUrl);
    }
  }
}
