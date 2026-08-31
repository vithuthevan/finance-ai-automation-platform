import { Component, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { ReportContextService, money } from './report-context.service';
import { ReportFiltersComponent } from './report-filters.component';
import { MonthlyTrend } from './report.models';

@Component({
  standalone: true,
  imports: [DatePipe, ReportFiltersComponent],
  template: `
    <div class="page">
      <h1>Monthly trend</h1>
      <app-report-filters [canExport]="false" [message]="message" (run)="load()"></app-report-filters>
      @if (rows.length === 0 && loaded) {
        <p class="empty-report">No monthly points in this range.</p>
      }
      @for (row of rows; track row.monthStart) {
        <div class="trend-row">
          <div class="trend-label">{{ row.monthStart | date:'MMM yyyy' }}</div>
          <div class="trend-bars">
            <div class="bar income" [style.width.%]="width(row.income)" [title]="'Income ' + format(row.income)"></div>
            <div class="bar expense" [style.width.%]="width(row.expenses)" [title]="'Expenses ' + format(row.expenses)"></div>
          </div>
          <div class="trend-values">
            <span>Inc {{ format(row.income) }}</span>
            <span>Exp {{ format(row.expenses) }}</span>
            <span>Net {{ format(row.profitOrLoss) }}</span>
          </div>
        </div>
      }
    </div>
  `
})
export class TrendsPage implements OnInit {
  rows: MonthlyTrend[] = [];
  message = '';
  loaded = false;
  max = 1;

  constructor(private api: ApiService, readonly ctx: ReportContextService) {}

  ngOnInit(): void {
    this.ctx.loadClients(() => this.load());
  }

  load(): void {
    if (!this.ctx.clientId()) {
      return;
    }
    this.api.get<MonthlyTrend[]>(`/api/v1/clients/${this.ctx.clientId()}/reports/trends`, this.ctx.params()).subscribe({
      next: (rows) => {
        this.rows = rows;
        this.max = Math.max(1, ...rows.flatMap((row) => [Number(row.income || 0), Number(row.expenses || 0)]));
        this.loaded = true;
      },
      error: (err) => this.message = err.error?.detail || 'Unable to load trend'
    });
  }

  width(value: unknown): number {
    return Math.max(2, (Number(value || 0) / this.max) * 100);
  }

  format(value: unknown): string {
    return money(value);
  }
}
