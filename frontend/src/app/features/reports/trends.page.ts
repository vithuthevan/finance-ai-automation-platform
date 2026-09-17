import { Component, OnInit, ViewChild } from '@angular/core';
import { finalize } from 'rxjs';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';
import { ApiService } from '../../core/services/api.service';
import { ReportContextService, money } from './report-context.service';
import { ReportFiltersComponent } from './report-filters.component';
import { MonthlyTrend } from './report.models';
import { ToastService } from '../../shared/toast.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';

@Component({
  standalone: true,
  imports: [ReportFiltersComponent, BaseChartDirective, PageHeaderComponent, LoadingStateComponent, EmptyStateComponent],
  template: `
    <div class="page">
      <app-page-header
        title="Monthly trends"
        subtitle="Compare monthly income, expenses, and net result across the selected date range." />
      <app-report-filters [canExport]="false" [loading]="loading || ctx.clientsLoading()" (run)="load()"></app-report-filters>
      @if (loading && !rows.length) {
        <app-loading-state message="Loading trend chart…" />
      } @else if (rows.length === 0 && loaded) {
        <app-empty-state title="No monthly points" description="Try widening the date range or run the report again." icon="insights" />
      }
      @if (rows.length) {
        <div class="chart-panel chart-wrap">
          <canvas baseChart
            [data]="chartData"
            [options]="chartOptions"
            [type]="'bar'">
          </canvas>
        </div>
      }
    </div>
  `
})
export class TrendsPage implements OnInit {
  @ViewChild(BaseChartDirective) chart?: BaseChartDirective;

  rows: MonthlyTrend[] = [];
  loaded = false;
  loading = false;
  chartData: ChartData<'bar'> = { labels: [], datasets: [] };
  chartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'bottom' },
      tooltip: {
        callbacks: {
          label: (ctx) => `${ctx.dataset.label}: ${money(ctx.parsed.y)}`
        }
      }
    },
    scales: {
      x: { stacked: false, grid: { display: false } },
      y: {
        beginAtZero: true,
        ticks: { callback: (value) => money(value) }
      }
    }
  };

  constructor(
    private api: ApiService,
    readonly ctx: ReportContextService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.ctx.loadClients(() => this.load());
  }

  load(): void {
    if (!this.ctx.clientId() || this.ctx.clientsError()) {
      return;
    }
    this.loading = true;
    this.api.get<MonthlyTrend[]>(`/api/v1/clients/${this.ctx.clientId()}/reports/trends`, this.ctx.params()).pipe(
      finalize(() => {
        this.loading = false;
        this.loaded = true;
      })
    ).subscribe({
      next: (rows) => {
        this.rows = rows;
        this.chartData = {
          labels: rows.map((row) => {
            const d = new Date(row.monthStart);
            return d.toLocaleDateString(undefined, { month: 'short', year: 'numeric' });
          }),
          datasets: [
            {
              label: 'Income',
              data: rows.map((row) => Number(row.income || 0)),
              backgroundColor: '#0f766e',
              borderRadius: 4
            },
            {
              label: 'Expenses',
              data: rows.map((row) => Number(row.expenses || 0)),
              backgroundColor: '#e87722',
              borderRadius: 4
            },
            {
              type: 'line' as const,
              label: 'Net',
              data: rows.map((row) => Number(row.profitOrLoss || 0)),
              borderColor: '#1c2434',
              backgroundColor: '#1c2434',
              tension: 0.25,
              yAxisID: 'y'
            } as any
          ]
        };
        this.chart?.update();
      },
      error: (err) => {
        this.rows = [];
        this.toast.error(err.error?.detail || 'Unable to load trend');
      }
    });
  }
}
