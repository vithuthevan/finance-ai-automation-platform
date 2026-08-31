import { Component, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { ReportContextService, money } from './report-context.service';
import { ReportFiltersComponent } from './report-filters.component';
import { saveBlob } from './report-download';
import { IncomeSummary } from './report.models';

@Component({
  standalone: true,
  imports: [DatePipe, ReportFiltersComponent],
  template: `
    <div class="page">
      <h1>Income report</h1>
      <app-report-filters [exporting]="exporting" [message]="message" (run)="load()" (export)="exportFile($event)"></app-report-filters>
      @if (report && !report.hasApprovedData) {
        <p class="empty-report">No approved income for this period. Draft and void records are excluded.</p>
      } @else if (report) {
        <section class="pnl">
          <header>
            <h2>{{ report.clientName }}</h2>
            <p>{{ report.from | date:'mediumDate' }} – {{ report.to | date:'mediumDate' }} · {{ report.currencyCode }}</p>
          </header>
          <p>Total {{ format(report.totalIncome) }} · {{ report.transactionCount }} transactions · Average {{ format(report.averageAmount) }}</p>
          <h3>By category</h3>
          <table class="pnl-table">
            @for (row of report.byCategory; track row.categoryId) {
              <tr>
                <td>{{ row.categoryCode }} {{ row.categoryName }}</td>
                <td class="num">{{ format(row.amount) }}</td>
                <td class="num hint">{{ row.percentageOfTotal }}%</td>
              </tr>
            }
            <tr class="total"><td>Total Income</td><td class="num">{{ format(report.totalIncome) }}</td><td></td></tr>
          </table>
          <h3>By payment method</h3>
          <table class="pnl-table">
            @for (row of report.byPaymentMethod; track row.name) {
              <tr>
                <td>{{ row.name }}</td>
                <td class="num">{{ format(row.amount) }}</td>
                <td>{{ row.count }}</td>
              </tr>
            }
          </table>
        </section>
      }
    </div>
  `
})
export class IncomeReportPage implements OnInit {
  report: IncomeSummary | null = null;
  message = '';
  exporting = false;

  constructor(private api: ApiService, readonly ctx: ReportContextService) {}

  ngOnInit(): void {
    this.ctx.loadClients(() => this.load());
  }

  load(): void {
    if (!this.ctx.clientId()) {
      return;
    }
    this.api.get<IncomeSummary>(`/api/v1/clients/${this.ctx.clientId()}/reports/income`, this.ctx.params()).subscribe({
      next: (report) => this.report = report,
      error: (err) => this.message = err.error?.detail || 'Unable to load income report'
    });
  }

  exportFile(format: string): void {
    this.exporting = true;
    this.api.downloadAttachment(`/api/v1/clients/${this.ctx.clientId()}/reports/income/export`, { ...this.ctx.params(), format }).subscribe({
      next: (file) => { saveBlob(file.blob, file.filename || `Income.${format}`); this.exporting = false; },
      error: () => { this.message = 'Export failed'; this.exporting = false; }
    });
  }

  format(value: unknown): string {
    return money(value);
  }
}
