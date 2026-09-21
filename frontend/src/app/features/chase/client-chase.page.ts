import { DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { ApiService } from '../../core/services/api.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { ToastService } from '../../shared/toast.service';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';

interface ChaseRun {
  id: string;
  clientId: string;
  clientName: string;
  sourceType: string;
  sourceId: string;
  status: string;
  startedAt: string;
  reminderCount: number;
  lastReminderAt?: string;
  lastDeliveryStatus: string;
}

type ChaseView = 'ACTIVE' | 'OVERDUE' | 'ESCALATED' | 'SUPPRESSED' | 'COMPLETED';

@Component({
  standalone: true,
  imports: [
    RouterLink, MatButtonModule, MatTabsModule, DatePipe, PageHeaderComponent,
    LoadingStateComponent, EmptyStateComponent, StatusBadgeComponent
  ],
  template: `
    <div class="page">
      <app-page-header title="Client chase" subtitle="Reminders for client-owned document and evidence blockers.">
        <a fpPageActions mat-stroked-button routerLink="/app/firm">Chase policy</a>
      </app-page-header>

      <mat-tab-group [(selectedIndex)]="tabIndex" (selectedIndexChange)="onTabChange()">
        <mat-tab label="Active"></mat-tab>
        <mat-tab label="Overdue"></mat-tab>
        <mat-tab label="Escalated"></mat-tab>
        <mat-tab label="Suppressed"></mat-tab>
        <mat-tab label="Completed"></mat-tab>
      </mat-tab-group>

      @if (loading) {
        <app-loading-state message="Loading chase runs…" />
      } @else if (!filteredRuns.length) {
        <app-empty-state title="No runs" description="Nothing in this view right now." icon="notifications_active" />
      } @else {
        <div class="table-scroll">
          <table class="data-table">
            <thead>
              <tr>
                <th>Client</th><th>Source</th><th>Status</th><th>Started</th><th>Last action</th>
                <th>Reminders</th><th>Delivery</th><th></th>
              </tr>
            </thead>
            <tbody>
              @for (run of filteredRuns; track run.id) {
                <tr>
                  <td><a [routerLink]="['/app/clients']" [queryParams]="{ q: run.clientName }">{{ run.clientName }}</a></td>
                  <td>{{ run.sourceType }}</td>
                  <td><app-status-badge [status]="run.status" /></td>
                  <td>{{ run.startedAt | date:'mediumDate' }}</td>
                  <td>{{ run.lastReminderAt ? (run.lastReminderAt | date:'medium') : '—' }}</td>
                  <td>{{ run.reminderCount }}</td>
                  <td>{{ deliveryLabel(run.lastDeliveryStatus) }}</td>
                  <td class="actions">
                    <button mat-button type="button" (click)="openSource(run)">View source</button>
                    @if (run.status === 'ACTIVE') {
                      <button mat-button type="button" (click)="suppress(run.id)">Suppress</button>
                    } @else if (run.status === 'SUPPRESSED') {
                      <button mat-button type="button" (click)="resume(run.id)">Resume</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .actions { white-space: nowrap; }
    mat-tab-group { margin-bottom: 16px; }
  `]
})
export class ClientChasePage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  runs: ChaseRun[] = [];
  loading = false;
  tabIndex = 0;

  private readonly views: ChaseView[] = ['ACTIVE', 'OVERDUE', 'ESCALATED', 'SUPPRESSED', 'COMPLETED'];

  ngOnInit(): void {
    this.reload();
  }

  get filteredRuns(): ChaseRun[] {
    const view = this.views[this.tabIndex] ?? 'ACTIVE';
    return this.runs.filter(run => this.matchesView(run, view));
  }

  onTabChange(): void {
    // view derived from tabIndex
  }

  reload(): void {
    this.loading = true;
    this.api.get<ChaseRun[]>('/api/v1/client-chase/runs').subscribe({
      next: (rows) => {
        this.runs = rows ?? [];
        this.loading = false;
      },
      error: () => {
        this.runs = [];
        this.loading = false;
        this.toast.error('Could not load chase runs.');
      }
    });
  }

  deliveryLabel(status: string): string {
    if (status === 'UNABLE_TO_SEND') return 'Unable to deliver';
    if (status === 'SENT') return 'Sent';
    return status || '—';
  }

  matchesView(run: ChaseRun, view: ChaseView): boolean {
    if (view === 'ACTIVE') return run.status === 'ACTIVE';
    if (view === 'SUPPRESSED') return run.status === 'SUPPRESSED';
    if (view === 'COMPLETED') return run.status === 'COMPLETED';
    if (view === 'OVERDUE') {
      return run.status === 'ACTIVE' && run.reminderCount > 0
        && run.lastDeliveryStatus === 'UNABLE_TO_SEND';
    }
    if (view === 'ESCALATED') {
      return run.status === 'ACTIVE' && run.reminderCount >= 3;
    }
    return false;
  }

  openSource(run: ChaseRun): void {
    if (run.sourceType === 'DOCUMENT_REQUEST') {
      this.router.navigate(['/app/requests'], { queryParams: { clientId: run.clientId } });
      return;
    }
    this.router.navigate(['/app/clients'], { queryParams: { q: run.clientName } });
  }

  suppress(id: string): void {
    this.api.post(`/api/v1/client-chase/runs/${id}/suppress`, {}).subscribe({
      next: () => { this.toast.success('Chase suppressed'); this.reload(); },
      error: (e) => this.toast.error(e?.error?.message || 'Failed')
    });
  }

  resume(id: string): void {
    this.api.post(`/api/v1/client-chase/runs/${id}/resume`, {}).subscribe({
      next: () => { this.toast.success('Chase resumed'); this.reload(); },
      error: (e) => this.toast.error(e?.error?.message || 'Failed')
    });
  }
}
