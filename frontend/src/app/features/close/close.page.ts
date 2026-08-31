import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { WorkQueueItem } from './close.models';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, MatTableModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule],
  template: `
    <div class="page">
      <h1>Month-end close</h1>
      <p class="hint">Check completeness, resolve blockers, then close. A closed period is a finalized bookkeeping period — not an audited financial statement.</p>
      <div class="toolbar-row">
        <mat-form-field><mat-label>Year</mat-label><input matInput type="number" [(ngModel)]="year" (change)="reloadFromStart()"></mat-form-field>
        <mat-form-field><mat-label>Month</mat-label>
          <mat-select [(ngModel)]="month" (selectionChange)="reloadFromStart()">
            @for (m of months; track m.value) { <mat-option [value]="m.value">{{ m.label }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <mat-form-field><mat-label>Search client</mat-label><input matInput [(ngModel)]="query" (keyup.enter)="reloadFromStart()"></mat-form-field>
        <mat-form-field><mat-label>Status</mat-label>
          <mat-select [(ngModel)]="status" (selectionChange)="reloadFromStart()">
            <mat-option value="">All</mat-option>
            <mat-option value="READY">Ready</mat-option>
            <mat-option value="OPEN">Open</mat-option>
            <mat-option value="IN_REVIEW">In review</mat-option>
            <mat-option value="CLOSED">Closed</mat-option>
            <mat-option value="REOPENED">Reopened</mat-option>
          </mat-select>
        </mat-form-field>
        <button mat-stroked-button (click)="reload()">Refresh</button>
      </div>
      @if (message) { <p class="hint">{{ message }}</p> }
      <table mat-table [dataSource]="rows" class="full-width">
        <ng-container matColumnDef="client"><th mat-header-cell *matHeaderCellDef>Client</th>
          <td mat-cell *matCellDef="let row">{{ row.clientName }}</td></ng-container>
        <ng-container matColumnDef="period"><th mat-header-cell *matHeaderCellDef>Period</th>
          <td mat-cell *matCellDef="let row">{{ monthLabel }} {{ row.year }}</td></ng-container>
        <ng-container matColumnDef="ready"><th mat-header-cell *matHeaderCellDef>Readiness</th>
          <td mat-cell *matCellDef="let row">{{ row.readinessPercent }}%</td></ng-container>
        <ng-container matColumnDef="blockers"><th mat-header-cell *matHeaderCellDef>Blockers</th>
          <td mat-cell *matCellDef="let row">{{ row.blockerCount }}</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let row" [class]="'status-' + row.displayStatus">{{ display(row.displayStatus) }}</td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            <a mat-button [routerLink]="row.periodId ? ['/app/close', row.clientId, row.periodId] : null" (click)="open(row, $event)">Open workspace</a>
          </td></ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      <div class="toolbar-row">
        <button mat-stroked-button (click)="prev()" [disabled]="page === 0">Previous</button>
        <span>Page {{ page + 1 }} · {{ total }} clients</span>
        <button mat-stroked-button (click)="next()" [disabled]="(page + 1) * size >= total">Next</button>
      </div>
    </div>
  `
})
export class ClosePage implements OnInit {
  year = new Date().getFullYear();
  month = new Date().getMonth() + 1;
  query = '';
  status = '';
  page = 0;
  size = 20;
  total = 0;
  rows: WorkQueueItem[] = [];
  message = '';
  columns = ['client', 'period', 'ready', 'blockers', 'status', 'actions'];
  months = [
    { value: 1, label: 'January' }, { value: 2, label: 'February' }, { value: 3, label: 'March' },
    { value: 4, label: 'April' }, { value: 5, label: 'May' }, { value: 6, label: 'June' },
    { value: 7, label: 'July' }, { value: 8, label: 'August' }, { value: 9, label: 'September' },
    { value: 10, label: 'October' }, { value: 11, label: 'November' }, { value: 12, label: 'December' }
  ];

  constructor(private api: ApiService, private router: Router, readonly auth: AuthService) {}

  ngOnInit(): void {
    this.reload();
  }

  get monthLabel(): string {
    return this.months.find((m) => m.value === this.month)?.label ?? '';
  }

  display(status: string): string {
    if (status === 'READY') return 'Ready';
    if (status === 'IN_REVIEW') return 'In review';
    if (status === 'REOPENED') return 'Reopened';
    if (status === 'CLOSED') return 'Closed';
    return 'Open';
  }

  reloadFromStart(): void {
    this.page = 0;
    this.reload();
  }

  reload(): void {
    this.message = '';
    this.api.get<{ content: WorkQueueItem[]; totalElements: number }>(`/api/v1/close/work-queue`, {
      year: this.year,
      month: this.month,
      status: this.status || undefined,
      q: this.query || undefined,
      page: this.page,
      size: this.size
    }).subscribe({
      next: (page) => {
        this.rows = page.content ?? [];
        this.total = page.totalElements ?? 0;
      },
      error: (err) => this.message = err.error?.detail || 'Unable to load close queue'
    });
  }

  prev(): void {
    this.page = Math.max(0, this.page - 1);
    this.reload();
  }

  next(): void {
    this.page += 1;
    this.reload();
  }

  open(row: WorkQueueItem, event: Event): void {
    if (row.periodId) {
      return;
    }
    event.preventDefault();
    if (!this.auth.hasRole('ADMIN', 'ACCOUNTANT')) {
      this.message = 'No period exists for this client yet.';
      return;
    }
    this.api.post(`/api/v1/clients/${row.clientId}/periods?year=${this.year}&month=${this.month}`).subscribe({
      next: (period: any) => this.router.navigate(['/app/close', row.clientId, period.id]),
      error: (err) => this.message = err.error?.detail || 'Unable to create period'
    });
  }
}
