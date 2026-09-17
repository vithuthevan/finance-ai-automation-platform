import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AgGridAngular } from 'ag-grid-angular';
import { ColDef, GridOptions, ICellRendererParams } from 'ag-grid-community';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { WorkQueueItem } from './close.models';
import { ToastService } from '../../shared/toast.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';

@Component({
  standalone: true,
  imports: [
    ReactiveFormsModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule,
    AgGridAngular, PageHeaderComponent
  ],
  template: `
    <div class="page">
      <app-page-header
        title="Month-end close"
        subtitle="Check completeness, resolve blockers, then close. A closed period is a finalized bookkeeping period — not an audited financial statement." />
      <form class="filter-bar" [formGroup]="filterForm" (ngSubmit)="reloadFromStart()" novalidate>
        <mat-form-field>
          <mat-label>Year</mat-label>
          <input matInput type="number" formControlName="year">
          @if (filterForm.controls.year.touched && filterForm.controls.year.hasError('required')) {
            <mat-error>Year is required</mat-error>
          } @else if (filterForm.controls.year.touched && filterForm.controls.year.hasError('min')) {
            <mat-error>Enter a year from 2000 onward</mat-error>
          } @else if (filterForm.controls.year.touched && filterForm.controls.year.hasError('max')) {
            <mat-error>Year cannot be more than one year ahead</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Month</mat-label>
          <mat-select formControlName="month" (selectionChange)="reloadFromStart()">
            @for (m of months; track m.value) { <mat-option [value]="m.value">{{ m.label }}</mat-option> }
          </mat-select>
          @if (filterForm.controls.month.touched && filterForm.controls.month.invalid) {
            <mat-error>Select a valid month</mat-error>
          }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Search client</mat-label>
          <input matInput formControlName="query" (keyup.enter)="reloadFromStart()">
        </mat-form-field>
        <mat-form-field>
          <mat-label>Status</mat-label>
          <mat-select formControlName="status" (selectionChange)="reloadFromStart()">
            <mat-option value="">All</mat-option>
            <mat-option value="READY">Ready</mat-option>
            <mat-option value="OPEN">Open</mat-option>
            <mat-option value="IN_REVIEW">In review</mat-option>
            <mat-option value="CLOSED">Closed</mat-option>
            <mat-option value="REOPENED">Reopened</mat-option>
          </mat-select>
        </mat-form-field>
        <div class="filter-actions">
          <button mat-stroked-button type="submit" [disabled]="filterForm.controls.year.invalid || filterForm.controls.month.invalid">
            Refresh
          </button>
        </div>
      </form>
      <ag-grid-angular
        class="ag-theme-quartz ag-grid-fp"
        [rowData]="rows"
        [columnDefs]="columnDefs"
        [defaultColDef]="defaultColDef"
        [gridOptions]="gridOptions"
        [domLayout]="'autoHeight'">
      </ag-grid-angular>
      <div class="toolbar-row">
        <button mat-stroked-button (click)="prev()" [disabled]="page === 0">Previous</button>
        <span>Page {{ page + 1 }} · {{ total }} clients</span>
        <button mat-stroked-button (click)="next()" [disabled]="(page + 1) * size >= total">Next</button>
      </div>
    </div>
  `
})
export class ClosePage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);

  private readonly maxYear = new Date().getFullYear() + 1;

  filterForm = this.fb.nonNullable.group({
    year: [new Date().getFullYear(), [Validators.required, Validators.min(2000), Validators.max(this.maxYear)]],
    month: [new Date().getMonth() + 1, [Validators.required, Validators.min(1), Validators.max(12)]],
    query: [''],
    status: ['']
  });

  page = 0;
  size = 20;
  total = 0;
  rows: WorkQueueItem[] = [];
  months = [
    { value: 1, label: 'January' }, { value: 2, label: 'February' }, { value: 3, label: 'March' },
    { value: 4, label: 'April' }, { value: 5, label: 'May' }, { value: 6, label: 'June' },
    { value: 7, label: 'July' }, { value: 8, label: 'August' }, { value: 9, label: 'September' },
    { value: 10, label: 'October' }, { value: 11, label: 'November' }, { value: 12, label: 'December' }
  ];

  defaultColDef: ColDef = { sortable: true, filter: true, resizable: true, flex: 1, minWidth: 100 };
  gridOptions: GridOptions = { context: { parent: this }, suppressCellFocus: true };

  columnDefs: ColDef[] = [
    { headerName: 'Client', field: 'clientName', minWidth: 160 },
    {
      headerName: 'Period',
      colId: 'period',
      valueGetter: (p) => `${p.context.parent.monthLabel} ${p.data.year}`
    },
    {
      headerName: 'Readiness',
      field: 'readinessPercent',
      valueFormatter: (p) => `${p.value}%`
    },
    { headerName: 'Blockers', field: 'blockerCount' },
    {
      headerName: 'Status',
      field: 'displayStatus',
      valueFormatter: (p) => p.context.parent.display(p.value),
      cellClass: (p) => p.value ? `status-${p.value}` : ''
    },
    {
      headerName: '',
      colId: 'actions',
      sortable: false,
      filter: false,
      maxWidth: 160,
      cellRenderer: (p: ICellRendererParams) => {
        const a = document.createElement('button');
        a.className = 'ag-fp-btn';
        a.textContent = 'Open workspace';
        a.addEventListener('click', (event) => p.context.parent.open(p.data, event));
        return a;
      }
    }
  ];

  ngOnInit(): void {
    this.reload();
  }

  get monthLabel(): string {
    return this.months.find((m) => m.value === this.filterForm.controls.month.value)?.label ?? '';
  }

  display(status: string): string {
    if (status === 'READY') return 'Ready';
    if (status === 'IN_REVIEW') return 'In review';
    if (status === 'REOPENED') return 'Reopened';
    if (status === 'CLOSED') return 'Closed';
    return 'Open';
  }

  reloadFromStart(): void {
    if (this.filterForm.controls.year.invalid || this.filterForm.controls.month.invalid) {
      this.filterForm.controls.year.markAsTouched();
      this.filterForm.controls.month.markAsTouched();
      return;
    }
    this.page = 0;
    this.reload();
  }

  reload(): void {
    if (this.filterForm.controls.year.invalid || this.filterForm.controls.month.invalid) {
      return;
    }
    const { year, month, query, status } = this.filterForm.getRawValue();
    this.api.get<{ content: WorkQueueItem[]; totalElements: number }>(`/api/v1/close/work-queue`, {
      year,
      month,
      status: status || undefined,
      q: query || undefined,
      page: this.page,
      size: this.size
    }).subscribe({
      next: (page) => {
        this.rows = page.content ?? [];
        this.total = page.totalElements ?? 0;
      },
      error: (err) => this.toast.error(err.error?.detail || 'Unable to load close queue')
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
      void this.router.navigate(['/app/close', row.clientId, row.periodId]);
      return;
    }
    event.preventDefault();
    if (!this.auth.hasRole('ADMIN', 'ACCOUNTANT')) {
      this.toast.info('No period exists for this client yet.');
      return;
    }
    const { year, month } = this.filterForm.getRawValue();
    this.api.post(`/api/v1/clients/${row.clientId}/periods?year=${year}&month=${month}`).subscribe({
      next: (period: any) => void this.router.navigate(['/app/close', row.clientId, period.id]),
      error: (err) => this.toast.error(err.error?.detail || 'Unable to create period')
    });
  }
}
