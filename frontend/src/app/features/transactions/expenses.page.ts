import { Component, inject, OnInit } from '@angular/core';

import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { FormsModule } from '@angular/forms';

import { MatFormFieldModule } from '@angular/material/form-field';

import { MatSelectModule } from '@angular/material/select';

import { MatInputModule } from '@angular/material/input';

import { MatButtonModule } from '@angular/material/button';

import { MatDatepickerModule } from '@angular/material/datepicker';

import { ActivatedRoute, RouterLink } from '@angular/router';

import { AgGridAngular } from 'ag-grid-angular';

import { ColDef, GridApi, GridOptions, ICellRendererParams } from 'ag-grid-community';

import { ApiService } from '../../core/services/api.service';

import { AuthService } from '../../core/auth/auth.service';

import { DATE_RANGE_ERROR, formatIsoDate, isInvalidDateRange, parseIsoDate } from '../../shared/date.util';

import { amountValidators, notFutureDateValidator } from '../../shared/form.validators';

import { money } from '../../shared/money';

import { ToastService } from '../../shared/toast.service';
import { ClientContextService } from '../../core/services/client-context.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { effect } from '@angular/core';
import { finalize } from 'rxjs';



@Component({

  standalone: true,

  imports: [

    ReactiveFormsModule, FormsModule, RouterLink, MatFormFieldModule, MatSelectModule,

    MatInputModule, MatButtonModule, MatDatepickerModule, AgGridAngular, PageHeaderComponent,
    LoadingStateComponent, EmptyStateComponent, ErrorStateComponent

  ],

  template: `

    <div class="page">

      <app-page-header
        title="Expenses"
        subtitle="Create and approve expense drafts for the active client. Use the header to switch clients." />

      <div class="filter-bar">

        <mat-form-field appearance="outline">

          <mat-label>Status</mat-label>

          <mat-select [(ngModel)]="filterStatus" (selectionChange)="onFilterChange()">

            <mat-option value="">All</mat-option>

            <mat-option value="DRAFT">Draft</mat-option>

            <mat-option value="APPROVED">Approved</mat-option>

            <mat-option value="VOID">Void</mat-option>

          </mat-select>

        </mat-form-field>

        <mat-form-field appearance="outline">

          <mat-label>Category</mat-label>

          <mat-select [(ngModel)]="filterCategoryId" (selectionChange)="onFilterChange()">

            <mat-option value="">All</mat-option>

            @for (cat of categories; track cat.id) { <mat-option [value]="cat.id">{{ cat.name }}</mat-option> }

          </mat-select>

        </mat-form-field>

        <mat-form-field appearance="outline">

          <mat-label>From</mat-label>

          <input matInput [matDatepicker]="fromPicker" [ngModel]="toDate(filterFrom)" (ngModelChange)="setFilterFrom($event)">

          <mat-datepicker-toggle matIconSuffix [for]="fromPicker"></mat-datepicker-toggle>

          <mat-datepicker #fromPicker></mat-datepicker>

        </mat-form-field>

        <mat-form-field appearance="outline">

          <mat-label>To</mat-label>

          <input matInput [matDatepicker]="toPicker" [ngModel]="toDate(filterTo)" (ngModelChange)="setFilterTo($event)">

          <mat-datepicker-toggle matIconSuffix [for]="toPicker"></mat-datepicker-toggle>

          <mat-datepicker #toPicker></mat-datepicker>

        </mat-form-field>

      </div>

      @if (filterDateRangeError) {

        <p class="field-error">{{ filterDateRangeError }}</p>

      }

      @if (auth.canWriteLedger()) {

        <section class="card-block">

          <h2>Create draft</h2>

          <form [formGroup]="form" (ngSubmit)="create()" novalidate>

            <div class="form-grid">

              <mat-form-field appearance="outline">

                <mat-label>Date</mat-label>

                <input matInput [matDatepicker]="datePicker" formControlName="transactionDate" [max]="maxTransactionDate">

                <mat-datepicker-toggle matIconSuffix [for]="datePicker"></mat-datepicker-toggle>

                <mat-datepicker #datePicker></mat-datepicker>

                @if (form.controls.transactionDate.touched && form.controls.transactionDate.hasError('required')) {

                  <mat-error>Date is required</mat-error>

                } @else if (form.controls.transactionDate.touched && form.controls.transactionDate.hasError('futureDate')) {

                  <mat-error>Date cannot be in the future</mat-error>

                }

              </mat-form-field>

              <mat-form-field appearance="outline">

                <mat-label>Category</mat-label>

                <mat-select formControlName="categoryId">

                  @for (cat of categories; track cat.id) { <mat-option [value]="cat.id">{{ cat.name }}</mat-option> }

                </mat-select>

                @if (form.controls.categoryId.touched && form.controls.categoryId.invalid) {

                  <mat-error>Category is required</mat-error>

                }

              </mat-form-field>

              <mat-form-field appearance="outline">

                <mat-label>Amount</mat-label>

                <input matInput type="number" step="0.01" min="0.01" formControlName="amount">

                @if (form.controls.amount.touched && form.controls.amount.hasError('required')) {

                  <mat-error>Amount is required</mat-error>

                } @else if (form.controls.amount.touched && form.controls.amount.hasError('min')) {

                  <mat-error>Amount must be greater than 0</mat-error>

                }

              </mat-form-field>

              <mat-form-field appearance="outline">

                <mat-label>Vendor</mat-label>

                <input matInput formControlName="vendorName">

                @if (form.controls.vendorName.touched && form.controls.vendorName.hasError('required')) {

                  <mat-error>Vendor is required</mat-error>

                } @else if (form.controls.vendorName.touched && form.controls.vendorName.hasError('maxlength')) {

                  <mat-error>Vendor must not exceed 200 characters</mat-error>

                }

              </mat-form-field>

              <mat-form-field appearance="outline" class="span-2">

                <mat-label>Description</mat-label>

                <textarea matInput rows="2" formControlName="description"></textarea>

              </mat-form-field>

            </div>

            <div class="form-actions">

              <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || creating">

                {{ creating ? 'Creating…' : 'Create draft' }}

              </button>

            </div>

          </form>

        </section>

      }

      @if (loadError) {
        <app-error-state title="Could not load expenses" [message]="loadError" (retry)="reload()" />
      } @else if (loading) {
        <app-loading-state message="Loading expenses…" />
      } @else if (!rows.length) {
        <app-empty-state
          title="No expenses match your filters"
          description="Create a draft above or adjust filters to see ledger items."
          icon="payments" />
      } @else {
        <div class="grid-scroll">
          <ag-grid-angular
            class="ag-theme-quartz ag-grid-fp"
            [rowData]="rows"
            [columnDefs]="columnDefs"
            [defaultColDef]="defaultColDef"
            [gridOptions]="gridOptions"
            [domLayout]="'autoHeight'">
          </ag-grid-angular>
        </div>
      }

      @if (selected) {

        <section class="card-block">

          <h2>Supporting documents</h2>

          <div class="doc-chip-list">

            @for (doc of supporting; track doc.id) {

              <div class="doc-chip">

                <span class="doc-name" [title]="doc.fileName">{{ doc.fileName }} · {{ doc.documentType }} · {{ doc.uploadedAt }}</span>

                <a mat-button [routerLink]="['/app/documents', clientId, doc.id]">Preview</a>

                <button mat-button type="button" (click)="download(doc)">Download</button>

              </div>

            }

          </div>

          @if (auth.hasRole('ADMIN', 'ACCOUNTANT') && selected.status !== 'VOID') {

            <form class="filter-bar" [formGroup]="attachForm" (ngSubmit)="attach()" novalidate>

              <mat-form-field appearance="outline">

                <mat-label>Attach document</mat-label>

                <mat-select formControlName="documentId">

                  @for (doc of unlinked; track doc.id) { <mat-option [value]="doc.id">{{ doc.fileName }}</mat-option> }

                </mat-select>

                @if (attachForm.controls.documentId.touched && attachForm.controls.documentId.invalid) {

                  <mat-error>Select a document to attach</mat-error>

                }

              </mat-form-field>

              <div class="filter-actions">

                <button mat-stroked-button type="submit" [disabled]="attachForm.invalid || attaching">

                  {{ attaching ? 'Linking…' : 'Link document' }}

                </button>

              </div>

            </form>

          }

        </section>

      }

    </div>

  `,

  styles: [`

    .grid-scroll {

      overflow-x: auto;

      -webkit-overflow-scrolling: touch;

    }



    .field-error {

      margin: -8px 0 12px;

    }

  `]

})

export class ExpensesPage implements OnInit {

  private readonly api = inject(ApiService);

  private readonly fb = inject(FormBuilder);

  private readonly route = inject(ActivatedRoute);

  private readonly toast = inject(ToastService);

  readonly auth = inject(AuthService);

  private readonly clientContext = inject(ClientContextService);

  clients: any[] = [];
  loading = false;
  loadError = '';

  categories: any[] = [];

  rows: any[] = [];

  supporting: any[] = [];

  unlinked: any[] = [];

  selected: any = null;

  creating = false;

  attaching = false;

  approvingId: string | null = null;

  voidingId: string | null = null;

  clientId = '';

  filterStatus = '';

  filterCategoryId = '';

  filterFrom = '';

  filterTo = '';

  readonly maxTransactionDate = new Date();

  private gridApi: GridApi | null = null;



  defaultColDef: ColDef = { sortable: true, filter: true, resizable: true, flex: 1, minWidth: 100 };

  gridOptions: GridOptions = {

    context: { parent: this },

    suppressCellFocus: true,

    onGridReady: (event) => {

      this.gridApi = event.api;

    }

  };



  columnDefs: ColDef[] = [

    { headerName: 'Date', field: 'transactionDate', minWidth: 110 },

    { headerName: 'Vendor', field: 'vendorName', minWidth: 140 },

    {

      headerName: 'Amount',

      field: 'amount',

      minWidth: 120,

      valueFormatter: (p) => money(p.value, p.data?.currencyCode)

    },

    {

      headerName: 'Status',

      field: 'status',

      cellClass: (p) => p.value ? `status-${p.value}` : ''

    },

    {

      headerName: 'Documents',

      colId: 'documents',

      valueGetter: (p) => p.data?.documentIds?.length || 0

    },

    {

      headerName: '',

      colId: 'actions',

      sortable: false,

      filter: false,

      minWidth: 220,

      cellRenderer: (p: ICellRendererParams) => {

        const wrap = document.createElement('div');

        wrap.className = 'ag-action-row';

        const parent = p.context.parent as ExpensesPage;

        const busy = !!parent.approvingId || !!parent.voidingId;

        if (parent.auth.hasRole('ADMIN', 'ACCOUNTANT') && p.data.status === 'DRAFT') {

          const approve = document.createElement('button');

          approve.className = 'ag-fp-btn';

          approve.type = 'button';

          approve.textContent = parent.approvingId === p.data.id ? 'Approving…' : 'Approve';

          approve.disabled = busy;

          approve.addEventListener('click', () => parent.approve(p.data));

          wrap.appendChild(approve);

        }

        if (parent.auth.hasRole('ADMIN', 'ACCOUNTANT') && p.data.status === 'APPROVED') {

          const voidBtn = document.createElement('button');

          voidBtn.className = 'ag-fp-btn';

          voidBtn.type = 'button';

          voidBtn.textContent = parent.voidingId === p.data.id ? 'Voiding…' : 'Void';

          voidBtn.disabled = busy;

          voidBtn.addEventListener('click', () => parent.voidRow(p.data));

          wrap.appendChild(voidBtn);

        }

        const docs = document.createElement('button');

        docs.className = 'ag-fp-btn';

        docs.type = 'button';

        docs.textContent = 'Documents';

        docs.addEventListener('click', () => parent.select(p.data));

        wrap.appendChild(docs);

        return wrap;

      }

    }

  ];



  form = this.fb.nonNullable.group({

    transactionDate: [null as Date | null, [Validators.required, notFutureDateValidator]],

    categoryId: ['', Validators.required],

    amount: [null as number | null, amountValidators],

    vendorName: ['', [Validators.required, Validators.maxLength(200)]],

    description: ['']

  });



  attachForm = this.fb.nonNullable.group({

    documentId: ['', Validators.required]

  });



  get filterDateRangeError(): string | null {

    return isInvalidDateRange(this.filterFrom, this.filterTo) ? DATE_RANGE_ERROR : null;

  }



  constructor() {
    effect(() => {
      const id = this.clientContext.clientId();
      if (id && id !== this.clientId && this.clients.length) {
        this.clientId = id;
        this.reload();
      }
    });
  }

  ngOnInit(): void {

    const query = this.route.snapshot.queryParamMap;

    this.filterStatus = query.get('status') ?? '';

    this.filterCategoryId = query.get('categoryId') ?? '';

    this.filterFrom = query.get('from') ?? '';

    this.filterTo = query.get('to') ?? '';

    const preferredClient = query.get('clientId') ?? '';

    this.api.list<any>('/api/v1/clients').subscribe((clients) => {

      this.clients = clients;

      const options = clients.map((c: { id: string; name: string }) => ({ id: c.id, name: c.name }));

      this.clientContext.setClients(options);

      this.clientId = this.clientContext.resolveInitial(preferredClient || null, options);

      this.clientContext.select(this.clientId);

      this.onFilterChange();

    });

    this.api.get<any[]>('/api/v1/categories', { categoryType: 'EXPENSE' }).subscribe((cats) => this.categories = cats);

  }



  toDate(value: string): Date | null {

    return parseIsoDate(value);

  }



  setFilterFrom(value: Date | null): void {

    this.filterFrom = formatIsoDate(value);

    this.onFilterChange();

  }



  setFilterTo(value: Date | null): void {

    this.filterTo = formatIsoDate(value);

    this.onFilterChange();

  }



  onFilterChange(): void {

    if (this.filterDateRangeError) {

      return;

    }

    this.reload();

  }



  reload(): void {

    if (!this.clientId || this.filterDateRangeError) {

      return;

    }

    this.clientContext.select(this.clientId);

    this.loading = true;
    this.loadError = '';

    this.api.get<any>(`/api/v1/clients/${this.clientId}/expenses`, {

      size: 50,

      status: this.filterStatus,

      categoryId: this.filterCategoryId,

      from: this.filterFrom,

      to: this.filterTo

    }).pipe(finalize(() => this.loading = false)).subscribe({
      next: (page) => this.rows = page.content ?? [],
      error: () => {
        this.rows = [];
        this.loadError = 'Unable to load expenses.';
      }
    });

  }



  create(): void {

    this.form.markAllAsTouched();

    if (this.form.invalid || this.creating) {

      if (this.form.invalid) {

        this.toast.error('Fix the highlighted fields before saving.');

      }

      return;

    }

    const raw = this.form.getRawValue();

    const amount = Number(raw.amount);

    if (!Number.isFinite(amount) || amount <= 0) {

      this.form.controls.amount.setErrors({ min: true });

      this.toast.error('Enter a valid amount greater than 0.');

      return;

    }

    this.creating = true;

    this.api.post(`/api/v1/clients/${this.clientId}/expenses`, {

      ...raw,

      amount,

      description: raw.description?.trim() || null,

      transactionDate: formatIsoDate(raw.transactionDate)

    }).subscribe({

      next: () => {

        this.creating = false;

        this.toast.success('Expense draft created');

        this.form.reset({ transactionDate: null, categoryId: '', amount: null, vendorName: '', description: '' });

        this.reload();

      },

      error: (err) => {

        this.creating = false;

        this.toast.error(err.error?.detail || 'Unable to create expense');

      }

    });

  }



  approve(row: any): void {

    if (this.approvingId || this.voidingId) {

      return;

    }

    this.approvingId = row.id;

    this.refreshActions();

    this.api.post(`/api/v1/clients/${this.clientId}/expenses/${row.id}/approve`).subscribe({

      next: () => {

        this.approvingId = null;

        this.toast.success('Expense approved');

        this.reload();

        this.refreshActions();

      },

      error: (err) => {

        this.approvingId = null;

        this.refreshActions();

        this.toast.error(err.error?.detail || 'Unable to approve');

      }

    });

  }



  voidRow(row: any): void {

    if (this.approvingId || this.voidingId) {

      return;

    }

    this.voidingId = row.id;

    this.refreshActions();

    this.api.post(`/api/v1/clients/${this.clientId}/expenses/${row.id}/void`, { reason: 'Voided from workspace' }).subscribe({

      next: () => {

        this.voidingId = null;

        this.toast.success('Expense voided');

        this.reload();

        this.refreshActions();

      },

      error: (err) => {

        this.voidingId = null;

        this.refreshActions();

        this.toast.error(err.error?.detail || 'Unable to void');

      }

    });

  }



  select(row: any): void {

    this.selected = row;

    this.supporting = [];

    this.attaching = false;

    this.attachForm.reset({ documentId: '' });

    (row.documentIds ?? []).forEach((id: string) => {

      this.api.get<any>(`/api/v1/clients/${this.clientId}/documents/${id}`).subscribe((doc) => {

        this.supporting = [...this.supporting, doc];

      });

    });

    this.api.get<any>(`/api/v1/clients/${this.clientId}/documents`, { linked: false, size: 50 }).subscribe((page) => {

      this.unlinked = page.content ?? [];

    });

  }



  attach(): void {

    this.attachForm.markAllAsTouched();

    if (!this.selected || this.attachForm.invalid || this.attaching) {

      if (this.attachForm.invalid) {

        this.toast.error('Select a document to attach.');

      }

      return;

    }

    this.attaching = true;

    this.api.post(`/api/v1/clients/${this.clientId}/expenses/${this.selected.id}/documents`, {

      documentId: this.attachForm.controls.documentId.value

    }).subscribe({

      next: () => {

        this.attaching = false;

        this.toast.success('Document linked');

        this.reload();

        this.api.get<any>(`/api/v1/clients/${this.clientId}/expenses/${this.selected.id}`).subscribe((row) => this.select(row));

      },

      error: (err) => {

        this.attaching = false;

        this.toast.error(err.error?.detail || 'Unable to link document');

      }

    });

  }



  download(doc: any): void {

    this.api.download(`/api/v1/clients/${this.clientId}/documents/${doc.id}/content`).subscribe((blob) => {

      const url = URL.createObjectURL(blob);

      const anchor = document.createElement('a');

      anchor.href = url;

      anchor.download = doc.fileName || 'document';

      anchor.click();

      URL.revokeObjectURL(url);

    });

  }



  private refreshActions(): void {

    this.gridApi?.refreshCells({ columns: ['actions'], force: true });

  }

}

