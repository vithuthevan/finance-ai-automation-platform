import { Component, OnInit, inject } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { AgGridAngular } from 'ag-grid-angular';
import { ColDef, GridOptions, ICellRendererParams, ValueFormatterParams } from 'ag-grid-community';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { FileDropzoneComponent } from '../../shared/file-dropzone.component';
import { ToastService } from '../../shared/toast.service';
import { DATE_RANGE_ERROR, formatIsoDate, isInvalidDateRange, parseIsoDate } from '../../shared/date.util';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { ClientContextService } from '../../core/services/client-context.service';
import { finalize } from 'rxjs';

@Component({
  standalone: true,
  imports: [
    ReactiveFormsModule, FormsModule, MatFormFieldModule, MatSelectModule, MatButtonModule, MatInputModule,
    MatDatepickerModule, AgGridAngular, FileDropzoneComponent, PageHeaderComponent,
    LoadingStateComponent, EmptyStateComponent, ErrorStateComponent
  ],
  template: `
    <div class="page">
      <app-page-header
        [title]="auth.isUploadOnly() ? 'My documents' : 'Document inbox'"
        subtitle="Upload evidence, track extraction status, and open items that need review." />

      @if (auth.canUploadDocuments()) {
        <section class="card-block upload-panel">
          <div class="toolbar-row">
            <div>
              <h2>Upload document</h2>
              <p class="hint">Add receipts, invoices, or bank files for extraction and review.</p>
            </div>
          </div>
          <form [formGroup]="uploadForm" (ngSubmit)="upload(false)" novalidate>
            <div class="form-grid">
              @if (!lockClient) {
                <mat-form-field appearance="outline">
                  <mat-label>Client</mat-label>
                  <mat-select formControlName="clientId">
                    @for (c of clients; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
                  </mat-select>
                  @if (uploadForm.controls.clientId.touched && uploadForm.controls.clientId.invalid) {
                    <mat-error>Client is required</mat-error>
                  }
                </mat-form-field>
              }
              <mat-form-field appearance="outline">
                <mat-label>Document type</mat-label>
                <mat-select formControlName="documentType">
                  <mat-option value="RECEIPT">Receipt</mat-option>
                  <mat-option value="PURCHASE_INVOICE">Purchase invoice</mat-option>
                  <mat-option value="SALES_INVOICE">Sales invoice</mat-option>
                  <mat-option value="CREDIT_NOTE">Credit note</mat-option>
                  <mat-option value="BANK_STATEMENT">Bank statement</mat-option>
                  <mat-option value="OTHER">Other</mat-option>
                </mat-select>
                @if (uploadForm.controls.documentType.touched && uploadForm.controls.documentType.invalid) {
                  <mat-error>Type is required</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline" class="span-2">
                <mat-label>Note</mat-label>
                <input matInput formControlName="note">
              </mat-form-field>
            </div>
            <app-file-dropzone
              accept=".pdf,.jpg,.jpeg,.png,.webp,.csv"
              [fileName]="file?.name || ''"
              hint="PDF, JPG, PNG, WEBP, or CSV"
              (fileSelected)="onFileSelected($event)">
            </app-file-dropzone>
            <div class="form-actions">
              <button mat-flat-button color="primary" type="submit" [disabled]="uploadForm.invalid || !file || uploading">
                {{ uploading ? 'Uploading…' : 'Upload' }}
              </button>
              @if (duplicateId) {
                <button mat-button type="button" (click)="upload(true)">Upload anyway</button>
              }
            </div>
          </form>
        </section>
      }

      <div class="filter-bar">
        @if (!lockClient) {
          <mat-form-field appearance="outline">
            <mat-label>Client</mat-label>
            <mat-select [(ngModel)]="clientId" (selectionChange)="onFilterChange()">
              <mat-option value="">All assigned</mat-option>
              @for (c of clients; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
            </mat-select>
          </mat-form-field>
        }
        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="status" (selectionChange)="onFilterChange()">
            <mat-option value="">All</mat-option>
            <mat-option value="NEEDS_REVIEW">Needs review</mat-option>
            <mat-option value="PROCESSING">Processing</mat-option>
            <mat-option value="EXTRACTED">Extracted</mat-option>
            <mat-option value="UPLOADED">Uploaded</mat-option>
            <mat-option value="FAILED">Failed</mat-option>
            <mat-option value="LINKED">Linked</mat-option>
            <mat-option value="REJECTED">Rejected</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Type</mat-label>
          <mat-select [(ngModel)]="documentType" (selectionChange)="onFilterChange()">
            <mat-option value="">All</mat-option>
            <mat-option value="RECEIPT">Receipt</mat-option>
            <mat-option value="PURCHASE_INVOICE">Purchase invoice</mat-option>
            <mat-option value="SALES_INVOICE">Sales invoice</mat-option>
            <mat-option value="CREDIT_NOTE">Credit note</mat-option>
            <mat-option value="BANK_STATEMENT">Bank statement</mat-option>
            <mat-option value="OTHER">Other</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Linked</mat-label>
          <mat-select [(ngModel)]="linked" (selectionChange)="onFilterChange()">
            <mat-option value="">All</mat-option>
            <mat-option value="true">Linked</mat-option>
            <mat-option value="false">Unlinked</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>From</mat-label>
          <input matInput [matDatepicker]="fromPicker" [ngModel]="toDate(from)" (ngModelChange)="setFrom($event)">
          <mat-datepicker-toggle matIconSuffix [for]="fromPicker"></mat-datepicker-toggle>
          <mat-datepicker #fromPicker></mat-datepicker>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>To</mat-label>
          <input matInput [matDatepicker]="toPicker" [ngModel]="toDate(to)" (ngModelChange)="setTo($event)">
          <mat-datepicker-toggle matIconSuffix [for]="toPicker"></mat-datepicker-toggle>
          <mat-datepicker #toPicker></mat-datepicker>
        </mat-form-field>
      </div>
      @if (filterDateRangeError) {
        <p class="field-error">{{ filterDateRangeError }}</p>
      }

      @if (loadError) {
        <app-error-state
          title="Could not load documents"
          [message]="loadError"
          [referenceId]="loadErrorRef"
          (retry)="reload()" />
      } @else if (loading) {
        <app-loading-state message="Loading documents…" />
      } @else if (!docs.length) {
        <app-empty-state
          title="No documents match your filters"
          description="Upload a file or adjust filters to see items in the inbox."
          icon="description" />
      } @else {
        <div class="grid-scroll">
          <ag-grid-angular
            class="ag-theme-quartz ag-grid-fp"
            [rowData]="docs"
            [columnDefs]="columnDefs"
            [defaultColDef]="defaultColDef"
            [gridOptions]="gridOptions"
            [domLayout]="'autoHeight'">
          </ag-grid-angular>
        </div>
        <div class="toolbar-row">
          <button mat-stroked-button type="button" (click)="prev()" [disabled]="page === 0">Previous</button>
          <span>Page {{ page + 1 }} · {{ total }} documents</span>
          <button mat-stroked-button type="button" (click)="next()" [disabled]="(page + 1) * size >= total">Next</button>
        </div>
      }
    </div>
  `
})
export class DocumentsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);
  private readonly clientContext = inject(ClientContextService);

  clients: any[] = [];
  loading = false;
  uploading = false;
  loadError = '';
  loadErrorRef = '';
  docs: any[] = [];
  clientId = '';
  status = '';
  documentType = '';
  linked = '';
  from = '';
  to = '';
  duplicateId = '';
  file: File | null = null;
  lockClient = false;
  page = 0;
  size = 20;
  total = 0;

  uploadForm = this.fb.nonNullable.group({
    clientId: ['', Validators.required],
    documentType: ['RECEIPT', Validators.required],
    note: ['']
  });

  defaultColDef: ColDef = { sortable: true, filter: true, resizable: true, flex: 1, minWidth: 110 };
  gridOptions: GridOptions = {
    context: { parent: this },
    suppressCellFocus: true
  };

  columnDefs: ColDef[] = [
    {
      headerName: 'Document',
      field: 'fileName',
      minWidth: 180,
      cellRenderer: (p: ICellRendererParams) => {
        const a = document.createElement('button');
        a.className = 'ag-fp-link';
        a.textContent = p.value;
        a.addEventListener('click', () => p.context.parent.openReview(p.data));
        return a;
      }
    },
    { headerName: 'Client', field: 'clientName' },
    { headerName: 'Type', field: 'documentType' },
    { headerName: 'Uploaded by', field: 'uploadedByName' },
    {
      headerName: 'Uploaded',
      field: 'uploadedAt',
      valueFormatter: (p: ValueFormatterParams) => p.value ? new Date(p.value).toLocaleString() : ''
    },
    {
      headerName: 'Status',
      field: 'status',
      cellClass: (p) => p.value ? `status-${p.value}` : ''
    },
    {
      headerName: 'Linked transaction',
      colId: 'linked',
      valueGetter: (p) => p.context.parent.linkedLabel(p.data)
    },
    {
      headerName: '',
      colId: 'actions',
      sortable: false,
      filter: false,
      maxWidth: 120,
      cellRenderer: (p: ICellRendererParams) => {
        const a = document.createElement('button');
        a.className = 'ag-fp-btn';
        a.textContent = 'Review';
        a.addEventListener('click', () => p.context.parent.openReview(p.data));
        return a;
      }
    }
  ];

  ngOnInit(): void {
    const query = this.route.snapshot.queryParamMap;
    if (query.get('status')) {
      this.status = query.get('status') ?? '';
    } else if (this.auth.hasRole('ADMIN', 'ACCOUNTANT')) {
      this.status = 'NEEDS_REVIEW';
    }
    this.from = query.get('from') ?? '';
    this.to = query.get('to') ?? '';
    this.linked = query.get('linked') ?? '';
    const preferredClient = query.get('clientId') ?? '';
    this.api.list<any>('/api/v1/clients').subscribe((clients) => {
      this.clients = clients;
      const options = clients.map((c: { id: string; name: string }) => ({ id: c.id, name: c.name }));
      this.clientContext.setClients(options);
      this.lockClient = this.auth.hasRole('BUSINESS_OWNER') && clients.length === 1;
      if (preferredClient && clients.some((client) => client.id === preferredClient)) {
        this.clientId = preferredClient;
        this.uploadForm.controls.clientId.setValue(preferredClient);
      } else if (this.lockClient) {
        this.clientId = clients[0].id;
        this.uploadForm.controls.clientId.setValue(clients[0].id);
      } else if (this.auth.hasRole('BUSINESS_OWNER') && clients.length > 0) {
        this.uploadForm.controls.clientId.setValue(clients[0].id);
      }
      this.reload();
    });
  }

  toDate(value: string): Date | null {
    return parseIsoDate(value);
  }

  setFrom(value: Date | null): void {
    this.from = formatIsoDate(value);
    this.onFilterChange();
  }

  setTo(value: Date | null): void {
    this.to = formatIsoDate(value);
    this.onFilterChange();
  }

  onFilterChange(): void {
    this.page = 0;
    this.reload();
  }

  get filterDateRangeError(): string | null {
    return isInvalidDateRange(this.from, this.to) ? DATE_RANGE_ERROR : null;
  }

  reload(): void {
    if (this.filterDateRangeError) {
      return;
    }
    if (this.clientId) {
      this.clientContext.select(this.clientId);
    }
    this.loading = true;
    this.loadError = '';
    this.api.get<any>('/api/v1/documents', {
      clientId: this.clientId || undefined,
      status: this.status || undefined,
      documentType: this.documentType || undefined,
      from: this.from || undefined,
      to: this.to || undefined,
      linked: this.linked === '' ? undefined : this.linked,
      page: this.page,
      size: this.size
    }).pipe(finalize(() => this.loading = false)).subscribe({
      next: (page) => {
        this.docs = page.content ?? [];
        this.total = page.totalElements ?? 0;
      },
      error: (err: HttpErrorResponse) => {
        this.docs = [];
        this.total = 0;
        this.loadError = err.error?.detail || 'Unable to load documents right now.';
        this.loadErrorRef = err.error?.correlationId || err.error?.traceId || '';
      }
    });
  }

  onFileSelected(file: File): void {
    this.file = file;
    this.duplicateId = '';
  }

  upload(allowDuplicate: boolean): void {
    this.uploadForm.markAllAsTouched();
    if (this.uploadForm.invalid) {
      this.toast.error('Fix the highlighted fields before uploading.');
      return;
    }
    if (!this.file) {
      this.toast.error('Select a file to upload.');
      return;
    }
    const raw = this.uploadForm.getRawValue();
    this.uploading = true;
    this.api.upload<any>(`/api/v1/clients/${raw.clientId}/documents`, this.file, {
      documentType: raw.documentType,
      description: raw.note,
      allowDuplicate: String(allowDuplicate)
    }).pipe(finalize(() => this.uploading = false)).subscribe({
      next: (doc) => {
        this.toast.success(doc.possibleDuplicate ? 'Uploaded with a duplicate warning.' : 'Document uploaded.');
        this.duplicateId = '';
        this.file = null;
        this.uploadForm.patchValue({ note: '' });
        this.reload();
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 409 || err.error?.errorCode === 'POSSIBLE_DUPLICATE') {
          this.duplicateId = err.error?.existingDocumentId ?? '';
          this.toast.info('Possible duplicate of an existing document.');
          return;
        }
        this.toast.error(err.error?.detail || 'Upload failed.');
      }
    });
  }

  openReview(row: any): void {
    void this.router.navigate(['/app/documents', row.clientId, row.id]);
  }

  linkedLabel(row: any): string {
    const expenses = row.linkedExpenseIds?.length ?? 0;
    const income = row.linkedIncomeIds?.length ?? 0;
    if (!expenses && !income) {
      return '—';
    }
    return `${expenses} expense / ${income} income`;
  }

  prev(): void {
    if (this.page === 0) {
      return;
    }
    this.page -= 1;
    this.reload();
  }

  next(): void {
    if ((this.page + 1) * this.size >= this.total) {
      return;
    }
    this.page += 1;
    this.reload();
  }
}
