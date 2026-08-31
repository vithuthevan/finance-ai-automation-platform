import { Component, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ReportContextService, money } from './report-context.service';
import { ReportFiltersComponent } from './report-filters.component';
import { saveBlob } from './report-download';
import {
  CategoryAmount,
  DocumentSupportSummary,
  PlComparison,
  PlSummary,
  ReportTransaction,
  TransactionStatusSummary
} from './report.models';

@Component({
  standalone: true,
  imports: [DatePipe, RouterLink, MatTableModule, MatButtonModule, ReportFiltersComponent],
  template: `
    <div class="page">
      <h1>{{ auth.hasRole('BUSINESS_OWNER') ? 'Financial summary' : 'Profit & Loss' }}</h1>
      <app-report-filters
        [showCompare]="!auth.hasRole('BUSINESS_OWNER')"
        [exporting]="exporting"
        [message]="message"
        (run)="load()"
        (export)="exportFile($event)">
      </app-report-filters>

      @if (summary && !summary.hasApprovedData) {
        <p class="empty-report">No approved transactions for this period. Draft and void records are excluded from official totals.</p>
      } @else if (summary) {
        <section class="pnl">
          <header>
            <h2>{{ summary.clientName }}</h2>
            <p>{{ summary.from | date:'mediumDate' }} – {{ summary.to | date:'mediumDate' }} · {{ summary.currencyCode }} · {{ summary.resultType }}</p>
          </header>

          <h3>Income</h3>
          <table class="pnl-table">
            @for (row of summary.incomeByCategory; track row.categoryId) {
              <tr class="clickable" (click)="drill('income', row)">
                <td>{{ row.categoryCode }} {{ row.categoryName }}@if (row.parentName) { <span class="hint"> · {{ row.parentName }}</span> }</td>
                <td class="num">{{ format(row.amount) }}</td>
                <td class="num hint">{{ row.percentageOfTotal }}%</td>
              </tr>
            }
            <tr class="total"><td>Total Income</td><td class="num">{{ format(summary.totalIncome) }}</td><td></td></tr>
          </table>

          <h3>Expenses</h3>
          <table class="pnl-table">
            @for (row of summary.expensesByCategory; track row.categoryId) {
              <tr class="clickable" (click)="drill('expenses', row)">
                <td>{{ row.categoryCode }} {{ row.categoryName }}@if (row.parentName) { <span class="hint"> · {{ row.parentName }}</span> }</td>
                <td class="num">{{ format(row.amount) }}</td>
                <td class="num hint">{{ row.percentageOfTotal }}%</td>
              </tr>
            }
            <tr class="total"><td>Total Expenses</td><td class="num">{{ format(summary.totalExpenses) }}</td><td></td></tr>
          </table>

          <table class="pnl-table net">
            <tr class="total">
              <td>NET {{ summary.resultType === 'LOSS' ? 'LOSS' : 'PROFIT' }}</td>
              <td class="num">{{ format(summary.netResult) }}</td>
              <td></td>
            </tr>
          </table>
        </section>

        @if (comparison && !auth.hasRole('BUSINESS_OWNER')) {
          <h2>Period comparison</h2>
          <p class="hint">{{ comparison.currentPeriod.from }} – {{ comparison.currentPeriod.to }} vs {{ comparison.previousPeriod.from }} – {{ comparison.previousPeriod.to }}</p>
          <table class="pnl-table">
            <tr>
              <th></th>
              <th class="num">Current</th>
              <th class="num">Comparison</th>
              <th class="num">Change</th>
              <th class="num">Change %</th>
            </tr>
            <tr>
              <td>Income</td>
              <td class="num">{{ format(comparison.currentPeriod.totalIncome) }}</td>
              <td class="num">{{ format(comparison.previousPeriod.totalIncome) }}</td>
              <td class="num" [class]="deltaClass(comparison.incomeDifference, 'income')">{{ format(comparison.incomeDifference) }}</td>
              <td class="num" [class]="deltaClass(comparison.incomeDifference, 'income')">{{ percent(comparison.incomeChangePercent) }}</td>
            </tr>
            <tr>
              <td>Expenses</td>
              <td class="num">{{ format(comparison.currentPeriod.totalExpenses) }}</td>
              <td class="num">{{ format(comparison.previousPeriod.totalExpenses) }}</td>
              <td class="num" [class]="deltaClass(comparison.expenseDifference, 'expense')">{{ format(comparison.expenseDifference) }}</td>
              <td class="num" [class]="deltaClass(comparison.expenseDifference, 'expense')">{{ percent(comparison.expenseChangePercent) }}</td>
            </tr>
            <tr class="total">
              <td>Profit / Loss</td>
              <td class="num">{{ format(comparison.currentPeriod.netResult) }}</td>
              <td class="num">{{ format(comparison.previousPeriod.netResult) }}</td>
              <td class="num" [class]="deltaClass(comparison.netDifference, 'income')">{{ format(comparison.netDifference) }}</td>
              <td class="num" [class]="deltaClass(comparison.netDifference, 'income')">{{ percent(comparison.netChangePercent) }}</td>
            </tr>
          </table>
        }

        @if (statuses && !auth.hasRole('BUSINESS_OWNER')) {
          <h2>Transaction status</h2>
          <p class="hint">Draft {{ statuses.draftIncome + statuses.draftExpenses }} · Approved {{ statuses.approvedIncome + statuses.approvedExpenses }} · Void {{ statuses.voidIncome + statuses.voidExpenses }}</p>
        }
        @if (documents && !auth.hasRole('BUSINESS_OWNER')) {
          <h2>Document support</h2>
          <p class="hint">Approved with documents {{ documents.approvedWithDocuments }} · Without documents {{ documents.approvedWithoutDocuments }} · Unlinked {{ documents.unlinkedDocuments }} · Awaiting review {{ documents.documentsAwaitingReview }}</p>
        }

        @if (drillRows.length && !auth.hasRole('BUSINESS_OWNER')) {
          <h2>Approved {{ drillType }} — {{ drillCategory }}</h2>
          <p>
            <a [routerLink]="drillType === 'income' ? '/app/income' : '/app/expenses'" [queryParams]="drillQuery">Open in ledger</a>
          </p>
          <table mat-table [dataSource]="drillRows" class="full-width">
            <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Date</th><td mat-cell *matCellDef="let row">{{ row.transactionDate }}</td></ng-container>
            <ng-container matColumnDef="party"><th mat-header-cell *matHeaderCellDef>Party</th><td mat-cell *matCellDef="let row">{{ row.vendorName || row.customerName }}</td></ng-container>
            <ng-container matColumnDef="amount"><th mat-header-cell *matHeaderCellDef>Amount</th><td mat-cell *matCellDef="let row">{{ format(row.amount) }}</td></ng-container>
            <tr mat-header-row *matHeaderRowDef="drillColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: drillColumns"></tr>
          </table>
        }
      }
    </div>
  `
})
export class ReportsPage implements OnInit {
  summary: PlSummary | null = null;
  comparison: PlComparison | null = null;
  statuses: TransactionStatusSummary | null = null;
  documents: DocumentSupportSummary | null = null;
  message = '';
  exporting = false;
  drillRows: ReportTransaction[] = [];
  drillType = '';
  drillCategory = '';
  drillQuery: Record<string, string> = {};
  drillColumns = ['date', 'party', 'amount'];

  constructor(
    private api: ApiService,
    readonly auth: AuthService,
    readonly ctx: ReportContextService
  ) {}

  ngOnInit(): void {
    this.ctx.loadClients(() => this.load());
  }

  load(): void {
    if (!this.ctx.clientId()) {
      return;
    }
    this.message = '';
    this.drillRows = [];
    this.api.get<PlSummary>(`/api/v1/clients/${this.ctx.clientId()}/reports/profit-and-loss`, this.ctx.params()).subscribe({
      next: (summary) => {
        this.summary = summary;
        this.loadComparison();
        this.loadSupport();
      },
      error: (err) => this.message = err.error?.detail || 'Unable to load report'
    });
  }

  exportFile(format: string): void {
    if (!this.ctx.clientId()) {
      return;
    }
    this.exporting = true;
    this.api.downloadAttachment(`/api/v1/clients/${this.ctx.clientId()}/reports/profit-and-loss/export`, {
      ...this.ctx.params(),
      format
    }).subscribe({
      next: (file) => {
        saveBlob(file.blob, file.filename || `PnL.${format}`);
        this.exporting = false;
      },
      error: () => {
        this.message = 'Export failed';
        this.exporting = false;
      }
    });
  }

  drill(type: 'income' | 'expenses', row: CategoryAmount): void {
    if (this.auth.hasRole('BUSINESS_OWNER')) {
      return;
    }
    this.drillType = type;
    this.drillCategory = row.categoryName;
    this.drillQuery = {
      clientId: this.ctx.clientId(),
      status: 'APPROVED',
      categoryId: row.categoryId,
      from: this.ctx.from(),
      to: this.ctx.to()
    };
    this.api.get<{ content: ReportTransaction[] }>(`/api/v1/clients/${this.ctx.clientId()}/${type}`, {
      status: 'APPROVED',
      categoryId: row.categoryId,
      from: this.ctx.from(),
      to: this.ctx.to(),
      size: 50
    }).subscribe((page) => this.drillRows = page.content ?? []);
  }

  format(value: unknown): string {
    return money(value);
  }

  percent(value: unknown): string {
    return value === null || value === undefined ? 'n/a' : `${value}%`;
  }

  deltaClass(value: unknown, kind: 'income' | 'expense'): string {
    const number = Number(value ?? 0);
    if (!number) {
      return '';
    }
    const favorable = kind === 'expense' ? number < 0 : number > 0;
    return favorable ? 'delta-favorable' : 'delta-watch';
  }

  private loadComparison(): void {
    const params: Record<string, string> = { ...this.ctx.params() };
    if (this.ctx.compareFrom() && this.ctx.compareTo()) {
      params['compareFrom'] = this.ctx.compareFrom();
      params['compareTo'] = this.ctx.compareTo();
    }
    this.api.get<PlComparison>(`/api/v1/clients/${this.ctx.clientId()}/reports/profit-and-loss/comparison`, params).subscribe({
      next: (comparison) => this.comparison = comparison,
      error: () => this.comparison = null
    });
  }

  private loadSupport(): void {
    if (this.auth.hasRole('BUSINESS_OWNER')) {
      return;
    }
    this.api.get<TransactionStatusSummary>(`/api/v1/clients/${this.ctx.clientId()}/reports/status-summary`).subscribe({
      next: (statuses) => this.statuses = statuses,
      error: () => this.statuses = null
    });
    this.api.get<DocumentSupportSummary>(`/api/v1/clients/${this.ctx.clientId()}/reports/document-support`, this.ctx.params()).subscribe({
      next: (documents) => this.documents = documents,
      error: () => this.documents = null
    });
  }
}
