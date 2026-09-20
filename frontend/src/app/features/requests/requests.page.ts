import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../shared/toast.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';

interface RequestCenterItem {
  id: string;
  clientId: string;
  clientName: string;
  title: string;
  documentType: string;
  dueDate: string | null;
  status: string;
  overdue: boolean;
  ageDays: number;
  nextAction: string;
  actionPath: string;
  reminderCount: number;
  periodYear: number | null;
  periodMonth: number | null;
}

@Component({
  standalone: true,
  imports: [
    FormsModule, MatButtonModule, MatFormFieldModule, MatSelectModule,
    PageHeaderComponent, StatusBadgeComponent, LoadingStateComponent, EmptyStateComponent
  ],
  template: `
    <div class="page">
      <app-page-header
        title="Document requests"
        subtitle="Track open, overdue, and uploaded client evidence across your portfolio." />

      <div class="filter-bar">
        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="status" (selectionChange)="load()">
            <mat-option value="">All open types</mat-option>
            <mat-option value="OPEN">Open</mat-option>
            <mat-option value="UPLOADED">Uploaded / needs review</mat-option>
            <mat-option value="COMPLETED">Completed</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Filter</mat-label>
          <mat-select [(ngModel)]="overdueOnly" (selectionChange)="load()">
            <mat-option [value]="false">All</mat-option>
            <mat-option [value]="true">Overdue only</mat-option>
          </mat-select>
        </mat-form-field>
        <button mat-stroked-button type="button" (click)="load()" [disabled]="loading">Refresh</button>
      </div>

      @if (loading) {
        <app-loading-state message="Loading requests…" />
      } @else if (!items.length) {
        <app-empty-state title="No requests" description="Create requests from a client's month-end period when evidence is missing." icon="assignment" />
      } @else {
        <div class="table-scroll">
          <table class="data-table">
            <thead>
              <tr>
                <th>Client</th>
                <th>Request</th>
                <th>Period</th>
                <th>Due</th>
                <th>Status</th>
                <th>Next action</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (row of items; track row.id) {
                <tr [class.overdue-row]="row.overdue">
                  <td>{{ row.clientName }}</td>
                  <td>
                    <div class="req-title">{{ row.title }}</div>
                    <div class="req-type">{{ row.documentType }}</div>
                  </td>
                  <td>{{ periodLabel(row) }}</td>
                  <td>{{ row.dueDate || '—' }}</td>
                  <td><app-status-badge [status]="row.status" /></td>
                  <td>{{ row.nextAction }}</td>
                  <td class="actions">
                    @if (row.status === 'OPEN' && row.overdue) {
                      <button mat-button type="button" (click)="remind(row)" [disabled]="remindingId === row.id">
                        {{ remindingId === row.id ? 'Sending…' : 'Send reminder' }}
                      </button>
                    }
                    <button mat-button type="button" (click)="open(row)">Open</button>
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
    .overdue-row { background: #fff4f4; }
    .req-title { font-weight: 600; }
    .req-type { font-size: 12px; color: var(--fp-muted); }
    .actions { white-space: nowrap; }
  `]
})
export class RequestsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  items: RequestCenterItem[] = [];
  loading = false;
  status = '';
  overdueOnly = false;
  remindingId = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const params: Record<string, string | number | boolean> = { page: 0, size: 100 };
    if (this.status) {
      params['status'] = this.status;
    }
    if (this.overdueOnly) {
      params['overdueOnly'] = true;
    }
    this.loading = true;
    this.api.get<{ content: RequestCenterItem[] }>('/api/v1/practice/document-requests', params).pipe(
      finalize(() => this.loading = false)
    ).subscribe({
      next: (res) => this.items = res.content ?? [],
      error: () => this.items = []
    });
  }

  periodLabel(row: RequestCenterItem): string {
    if (row.periodYear && row.periodMonth) {
      return `${row.periodYear}-${String(row.periodMonth).padStart(2, '0')}`;
    }
    return '—';
  }

  open(row: RequestCenterItem): void {
    if (row.periodYear && row.periodMonth) {
      this.router.navigate(['/app/close', row.clientId], { queryParams: { remind: row.id } });
      return;
    }
    this.router.navigate(['/app/close', row.clientId]);
  }

  remind(row: RequestCenterItem): void {
    this.remindingId = row.id;
    this.api.post(`/api/v1/clients/${row.clientId}/document-requests/${row.id}/remind`).subscribe({
      next: () => {
        this.toast.success('Reminder sent');
        this.remindingId = '';
        this.load();
      },
      error: (err) => {
        this.toast.error(err?.error?.detail ?? 'Could not send reminder');
        this.remindingId = '';
      }
    });
  }
}
