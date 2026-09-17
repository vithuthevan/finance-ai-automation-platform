import { Component, OnInit } from '@angular/core';
import { finalize } from 'rxjs';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { ReportContextService, money } from './report-context.service';
import { ReportFiltersComponent } from './report-filters.component';
import { saveBlob } from './report-download';
import { ExpenseSummary } from './report.models';
import { ToastService } from '../../shared/toast.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';

@Component({
  standalone: true,
  imports: [DatePipe, ReportFiltersComponent, PageHeaderComponent],
  template: `
    <div class="page">
      <app-page-header
        title="Expense report"
        subtitle="Analyze approved expenses by category and largest transactions for the selected period." />
      <app-report-filters [exporting]="exporting" [loading]="loading || ctx.clientsLoading()" [message]="message" (run)="load()" (export)="exportFile($event)"></app-report-filters>
      @if (report && !report.hasApprovedData) {
        <p class="empty-report">No approved expenses for this period. Draft and void records are excluded.</p>
      } @else if (report) {
        <section class="pnl">
          <header>
            <h2>{{ report.clientName }}</h2>
            <p>{{ report.from | date:'mediumDate' }} – {{ report.to | date:'mediumDate' }} · {{ report.currencyCode }}</p>
          </header>
          <p>Total {{ format(report.totalExpenses) }} · {{ report.transactionCount }} transactions · Average {{ format(report.averageAmount) }}</p>
          <h3>By category</h3>
          <table class="pnl-table">
            @for (row of report.byCategory; track row.categoryId) {
              <tr>
                <td>{{ row.categoryCode }} {{ row.categoryName }}</td>
                <td class="num">{{ format(row.amount) }}</td>
                <td class="num hint">{{ row.percentageOfTotal }}%</td>
              </tr>
            }
            <tr class="total"><td>Total Expenses</td><td class="num">{{ format(report.totalExpenses) }}</td><td></td></tr>
          </table>
          <h3>Largest expenses</h3>
          <table class="pnl-table">
            @for (row of report.largestExpenses; track row.id) {
              <tr>
                <td>{{ row.transactionDate }} · {{ row.partyName }} · {{ row.categoryName }}</td>
                <td class="num">{{ format(row.amount) }}</td>
              </tr>
            }
          </table>
        </section>
      }
    </div>
  `
})
export class ExpenseReportPage implements OnInit {
  report: ExpenseSummary | null = null;
  message = '';
  loading = false;
  exporting = false;

  constructor(private api: ApiService, readonly ctx: ReportContextService, private toast: ToastService) {}

  ngOnInit(): void {
    this.ctx.loadClients(() => this.load());
  }

  load(): void {
    if (!this.ctx.clientId() || this.ctx.clientsError()) {
      return;
    }
    this.message = '';
    this.loading = true;
    this.api.get<ExpenseSummary>(`/api/v1/clients/${this.ctx.clientId()}/reports/expenses`, this.ctx.params()).pipe(
      finalize(() => this.loading = false)
    ).subscribe({
      next: (report) => this.report = report,
      error: (err) => this.message = err.error?.detail || 'Unable to load expense report'
    });
  }

  exportFile(format: string): void {
    this.exporting = true;
    this.api.downloadAttachment(`/api/v1/clients/${this.ctx.clientId()}/reports/expenses/export`, { ...this.ctx.params(), format }).subscribe({
      next: (file) => { saveBlob(file.blob, file.filename || `Expenses.${format}`); this.exporting = false; },
      error: () => { this.toast.error('Export failed'); this.exporting = false; }
    });
  }

  format(value: unknown): string {
    return money(value);
  }
}
