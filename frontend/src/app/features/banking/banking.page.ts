import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ClientContextService } from '../../core/services/client-context.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { FileDropzoneComponent } from '../../shared/file-dropzone.component';
import { ToastService } from '../../shared/toast.service';
import { DATE_RANGE_ERROR, formatIsoDate, isInvalidDateRange } from '../../shared/date.util';
import {
  BankAccount,
  BankImportPreview,
  BankImportRow,
  BankTransactionRow,
  CategoryOption,
  MatchSuggestion,
  ReconciliationSummary
} from './banking.models';

@Component({
  standalone: true,
  imports: [
    ReactiveFormsModule, FormsModule, RouterLink, MatTableModule, MatFormFieldModule, MatSelectModule,
    MatInputModule, MatButtonModule, MatCardModule, MatTabsModule, MatDatepickerModule,
    FileDropzoneComponent, PageHeaderComponent, StatusBadgeComponent
  ],
  template: `
    <div class="page">
      <app-page-header
        title="Banking"
        subtitle="Import bank CSV statements, match against approved books, and resolve differences before month-end close." />

      <div class="filter-bar">
        <mat-form-field appearance="outline">
          <mat-label>Client</mat-label>
          <mat-select [(ngModel)]="clientId" (selectionChange)="onClientChange()">
            @for (c of clients; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
          </mat-select>
        </mat-form-field>
      </div>

      <mat-tab-group [(selectedIndex)]="tabIndex">
        <mat-tab label="Accounts">
          @if (canOperate) {
            <section class="card-block">
              <h2>Add bank account</h2>
              <form [formGroup]="accountForm" (ngSubmit)="createAccount()" novalidate>
                <div class="form-grid">
                  <mat-form-field appearance="outline">
                    <mat-label>Bank</mat-label>
                    <input matInput formControlName="bankName">
                    @if (accountForm.controls.bankName.touched && accountForm.controls.bankName.invalid) {
                      <mat-error>Bank is required</mat-error>
                    }
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Account name</mat-label>
                    <input matInput formControlName="accountName">
                    @if (accountForm.controls.accountName.touched && accountForm.controls.accountName.invalid) {
                      <mat-error>Account name is required</mat-error>
                    }
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Account no. (masked)</mat-label>
                    <input matInput formControlName="maskedAccountNumber">
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Currency</mat-label>
                    <input matInput formControlName="currency" maxlength="3">
                    @if (accountForm.controls.currency.touched && accountForm.controls.currency.invalid) {
                      <mat-error>Use a 3-letter currency code</mat-error>
                    }
                  </mat-form-field>
                </div>
                <div class="form-actions">
                  <button mat-flat-button color="primary" type="submit" [disabled]="accountForm.invalid">Save account</button>
                </div>
              </form>
            </section>
          }
          <table mat-table [dataSource]="accounts" class="full-width">
            <ng-container matColumnDef="bank"><th mat-header-cell *matHeaderCellDef>Bank</th><td mat-cell *matCellDef="let row">{{ row.bankName }}</td></ng-container>
            <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Account</th><td mat-cell *matCellDef="let row">{{ row.accountName }}</td></ng-container>
            <ng-container matColumnDef="number"><th mat-header-cell *matHeaderCellDef>Number</th><td mat-cell *matCellDef="let row">{{ row.maskedAccountNumber }}</td></ng-container>
            <ng-container matColumnDef="currency"><th mat-header-cell *matHeaderCellDef>Currency</th><td mat-cell *matCellDef="let row">{{ row.currency }}</td></ng-container>
            <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th><td mat-cell *matCellDef="let row">{{ row.active ? 'Active' : 'Inactive' }}</td></ng-container>
            <tr mat-header-row *matHeaderRowDef="accountColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: accountColumns"></tr>
          </table>
        </mat-tab>

        <mat-tab label="Import">
          @if (!canOperate) {
            <p class="hint">Read-only access.</p>
          } @else {
            <section class="card-block">
              <h2>Import CSV statement</h2>
              <form [formGroup]="importForm" novalidate>
                <div class="form-grid">
                  <mat-form-field appearance="outline" class="span-2">
                    <mat-label>Bank account</mat-label>
                    <mat-select formControlName="bankAccountId">
                      @for (a of accounts; track a.id) { <mat-option [value]="a.id">{{ a.bankName }} · {{ a.accountName }}</mat-option> }
                    </mat-select>
                    @if (importForm.controls.bankAccountId.touched && importForm.controls.bankAccountId.invalid) {
                      <mat-error>Bank account is required</mat-error>
                    }
                  </mat-form-field>
                </div>
                <app-file-dropzone
                  accept=".csv"
                  [disabled]="previewing || importing"
                  [fileName]="file?.name || ''"
                  label="Drop bank CSV here or browse"
                  hint="CSV statement export"
                  (fileSelected)="onFileSelected($event)">
                </app-file-dropzone>
                <h3>Column mapping</h3>
                <div class="form-grid">
                  <mat-form-field appearance="outline">
                    <mat-label>Date col</mat-label>
                    <input matInput type="number" min="0" formControlName="dateColumn">
                    @if (importForm.controls.dateColumn.touched && importForm.controls.dateColumn.invalid) {
                      <mat-error>Required (≥ 0)</mat-error>
                    }
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Description col</mat-label>
                    <input matInput type="number" min="0" formControlName="descriptionColumn">
                    @if (importForm.controls.descriptionColumn.touched && importForm.controls.descriptionColumn.invalid) {
                      <mat-error>Required (≥ 0)</mat-error>
                    }
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Reference col</mat-label>
                    <input matInput type="number" min="0" formControlName="referenceColumn">
                    @if (importForm.controls.referenceColumn.touched && importForm.controls.referenceColumn.invalid) {
                      <mat-error>Required (≥ 0)</mat-error>
                    }
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Debit col</mat-label>
                    <input matInput type="number" min="0" formControlName="debitColumn">
                    @if (importForm.controls.debitColumn.touched && importForm.controls.debitColumn.invalid) {
                      <mat-error>Required (≥ 0)</mat-error>
                    }
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Credit col</mat-label>
                    <input matInput type="number" min="0" formControlName="creditColumn">
                    @if (importForm.controls.creditColumn.touched && importForm.controls.creditColumn.invalid) {
                      <mat-error>Required (≥ 0)</mat-error>
                    }
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Balance col</mat-label>
                    <input matInput type="number" min="0" formControlName="balanceColumn">
                    @if (importForm.controls.balanceColumn.touched && importForm.controls.balanceColumn.invalid) {
                      <mat-error>Required (≥ 0)</mat-error>
                    }
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="span-2">
                    <mat-label>Profile name (optional)</mat-label>
                    <input matInput formControlName="profileName">
                  </mat-form-field>
                </div>
                <div class="form-actions">
                  <button mat-stroked-button type="button" (click)="previewImport()" [disabled]="!canImport || previewing || importing">Preview</button>
                  <button mat-flat-button color="primary" type="button" (click)="runImport()" [disabled]="!canImport || previewing || importing">Import</button>
                </div>
              </form>
              @if (preview) {
                <p>Rows: {{ preview.rowsDetected }} · Valid: {{ preview.validRows }} · Invalid: {{ preview.invalidRows }} · Potential duplicates: {{ preview.potentialDuplicates }}</p>
                @if (preview.periodFrom) { <p class="hint">Detected period {{ preview.periodFrom }} – {{ preview.periodTo }}</p> }
                @for (row of preview.sampleRows; track row.lineNumber) {
                  <p class="text-ellipsis" [title]="row.description">{{ row.txnDate }} · {{ row.description }} · {{ row.debit || row.credit }}</p>
                }
                @for (err of preview.errors; track err.lineNumber) {
                  <p class="hint">Row {{ err.lineNumber }}: {{ err.message }}</p>
                }
              }
            </section>
            <section class="card-block">
              <h2>Recent imports</h2>
              @for (imp of imports; track imp.id) {
                <p class="text-ellipsis" [title]="imp.fileName">{{ imp.fileName }} · {{ imp.importedCount }} imported · {{ imp.duplicateCount }} duplicates · {{ imp.failedCount }} failed</p>
              }
            </section>
          }
        </mat-tab>

        <mat-tab label="Reconciliation">
          <form class="filter-bar" [formGroup]="filterForm">
            <mat-form-field appearance="outline">
              <mat-label>Bank account</mat-label>
              <mat-select formControlName="bankAccountId" (selectionChange)="onFilterChange()">
                <mat-option value="">All accounts</mat-option>
                @for (a of accounts; track a.id) { <mat-option [value]="a.id">{{ a.bankName }} · {{ a.accountName }}</mat-option> }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Status</mat-label>
              <mat-select formControlName="statusFilter" (selectionChange)="reloadTransactions()">
                <mat-option value="">All</mat-option>
                <mat-option value="UNMATCHED">Unmatched</mat-option>
                <mat-option value="SUGGESTED">Suggested</mat-option>
                <mat-option value="MATCHED">Matched</mat-option>
                <mat-option value="PENDING_APPROVAL">Pending approval</mat-option>
                <mat-option value="IGNORED">Ignored</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>From</mat-label>
              <input matInput [matDatepicker]="fromPicker" formControlName="fromDate" (dateChange)="onFilterChange()">
              <mat-datepicker-toggle matIconSuffix [for]="fromPicker"></mat-datepicker-toggle>
              <mat-datepicker #fromPicker></mat-datepicker>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>To</mat-label>
              <input matInput [matDatepicker]="toPicker" formControlName="toDate" (dateChange)="onFilterChange()">
              <mat-datepicker-toggle matIconSuffix [for]="toPicker"></mat-datepicker-toggle>
              <mat-datepicker #toPicker></mat-datepicker>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Search</mat-label>
              <input matInput formControlName="searchQuery" (keyup.enter)="reloadTransactions()">
            </mat-form-field>
            <div class="filter-actions">
              <button mat-stroked-button type="button" (click)="onFilterChange()" [disabled]="!!filterDateRangeError()">Refresh</button>
            </div>
          </form>
          @if (filterDateRangeError()) {
            <p class="field-error">{{ filterDateRangeError() }}</p>
          }

          @if (summary) {
            <div class="recon-progress" role="status" aria-label="Reconciliation progress">
              <div>
                <div class="label">Reconciliation progress</div>
                <div class="value fp-num">{{ summary.reconciliationPercent }}% resolved</div>
                <p class="hint">{{ summary.matched }} matched · {{ summary.unmatched }} unmatched · {{ summary.ignored }} ignored</p>
              </div>
              <div class="recon-progress__bar" aria-hidden="true">
                <div class="recon-progress__fill" [style.width.%]="summary.reconciliationPercent"></div>
              </div>
            </div>
            <div class="grid-2">
              <mat-card class="metric-card"><div class="label">Reconciliation</div><div class="value fp-num">{{ summary.reconciliationPercent }}%</div></mat-card>
              <mat-card class="metric-card"><div class="label">Matched</div><div class="value">{{ summary.matched }}</div></mat-card>
              <mat-card class="metric-card"><div class="label">Suggested</div><div class="value">{{ summary.suggested }}</div></mat-card>
              <mat-card class="metric-card"><div class="label">Unmatched</div><div class="value">{{ summary.unmatched }}</div></mat-card>
              <mat-card class="metric-card"><div class="label">Ignored</div><div class="value">{{ summary.ignored }}</div></mat-card>
              <mat-card class="metric-card"><div class="label">Pending approval</div><div class="value">{{ summary.pendingApproval }}</div></mat-card>
            </div>
          }

          @for (row of transactions; track row.id) {
            <mat-card class="metric-card reconcile-card">
              <div class="reconcile-head">
                <strong>{{ row.txnDate }}</strong>
                <span class="text-ellipsis" [title]="row.description">{{ row.description }}</span>
                <span class="amount">{{ row.debit ? ('-' + row.debit) : ('+' + row.credit) }}</span>
                <app-status-badge [status]="row.matchStatus" />
              </div>
              @if (row.suggestions?.length) {
                <p class="hint">Suggested match:</p>
                @for (s of row.suggestions; track s.ledgerId) {
                  <div class="suggestion-row">
                    <span class="text-ellipsis" [title]="suggestionLabel(s)">{{ suggestionLabel(s) }}</span>
                    @if (canOperate && row.matchStatus !== 'MATCHED' && row.matchStatus !== 'IGNORED') {
                      <div class="form-actions">
                        <button mat-flat-button color="primary" type="button" (click)="confirm(row, s)">Confirm</button>
                        @if (s.matchId) { <button mat-button type="button" (click)="reject(row, s.matchId!)">Reject</button> }
                      </div>
                    }
                  </div>
                }
              }
              @if (canOperate && row.matchStatus !== 'MATCHED' && row.matchStatus !== 'IGNORED') {
                <div class="form-actions reconcile-actions">
                  @if (row.debit) {
                    <button mat-stroked-button type="button" (click)="createExpense(row)">Create expense (draft)</button>
                  }
                  @if (row.credit) {
                    <button mat-stroked-button type="button" (click)="createIncome(row)">Create income (draft)</button>
                  }
                  <button mat-stroked-button type="button" (click)="requestDocument(row)">Request document</button>
                  @if (ignoringRowId !== row.id) {
                    <button mat-button type="button" (click)="startIgnore(row)">Ignore</button>
                  }
                </div>
                @if (ignoringRowId === row.id) {
                  <form class="ignore-row" [formGroup]="ignoreForm" (ngSubmit)="submitIgnore(row)">
                    <mat-form-field appearance="outline" class="full-width">
                      <mat-label>Reason for ignoring</mat-label>
                      <input matInput formControlName="reason">
                      @if (ignoreForm.controls.reason.touched && ignoreForm.controls.reason.invalid) {
                        <mat-error>An ignore reason is required</mat-error>
                      }
                    </mat-form-field>
                    <button mat-stroked-button color="warn" type="submit" [disabled]="ignoreForm.invalid || ignoring">Ignore line</button>
                    <button mat-button type="button" (click)="cancelIgnore()" [disabled]="ignoring">Cancel</button>
                  </form>
                }
              }
              @if (canOperate && row.matchStatus === 'MATCHED') {
                <button mat-button type="button" (click)="unmatch(row)">Unmatch</button>
              }
              @if (row.pendingExpenseId) {
                <p class="hint">Draft expense created — <a routerLink="/app/expenses" [queryParams]="{ clientId: clientId, status: 'DRAFT' }">approve in Expenses</a>, then confirm match.</p>
              }
              @if (row.pendingIncomeId) {
                <p class="hint">Draft income created — <a routerLink="/app/income" [queryParams]="{ clientId: clientId, status: 'DRAFT' }">approve in Income</a>, then confirm match.</p>
              }
            </mat-card>
          }
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .reconcile-card { margin-bottom: 12px; }
    .reconcile-head {
      display: grid;
      grid-template-columns: 100px minmax(0, 1fr) auto auto;
      gap: 10px 12px;
      align-items: center;
      margin-bottom: 8px;
    }
    .reconcile-head .amount { font-variant-numeric: tabular-nums; font-weight: 600; }
    .suggestion-row {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 8px 12px;
      align-items: center;
      margin-bottom: 8px;
    }
    .reconcile-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
    }
    .ignore-row {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      align-items: flex-start;
      margin-top: 8px;
    }
    .ignore-row mat-form-field {
      flex: 1 1 240px;
      min-width: 0;
    }
    @media (max-width: 720px) {
      .reconcile-head, .suggestion-row { grid-template-columns: 1fr; }
      .reconcile-actions button { flex: 1 1 calc(50% - 4px); min-width: 0; }
    }
  `]
})
export class BankingPage implements OnInit {
  private readonly clientContext = inject(ClientContextService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);

  clients: any[] = [];
  accounts: BankAccount[] = [];
  imports: BankImportRow[] = [];
  transactions: BankTransactionRow[] = [];
  categories: CategoryOption[] = [];
  preview: BankImportPreview | null = null;
  summary: ReconciliationSummary | null = null;
  clientId = '';
  file: File | null = null;
  tabIndex = 0;
  previewing = false;
  importing = false;
  ignoringRowId: string | null = null;
  ignoring = false;
  accountColumns = ['bank', 'name', 'number', 'currency', 'status'];

  accountForm = this.fb.nonNullable.group({
    bankName: ['', Validators.required],
    accountName: ['', Validators.required],
    maskedAccountNumber: [''],
    currency: ['LKR', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]]
  });

  importForm = this.fb.nonNullable.group({
    bankAccountId: ['', Validators.required],
    dateColumn: [0, [Validators.required, Validators.min(0)]],
    descriptionColumn: [1, [Validators.required, Validators.min(0)]],
    referenceColumn: [2, [Validators.required, Validators.min(0)]],
    debitColumn: [3, [Validators.required, Validators.min(0)]],
    creditColumn: [4, [Validators.required, Validators.min(0)]],
    balanceColumn: [5, [Validators.required, Validators.min(0)]],
    profileName: ['']
  });

  filterForm = this.fb.nonNullable.group({
    bankAccountId: [''],
    statusFilter: [''],
    fromDate: [null as Date | null],
    toDate: [null as Date | null],
    searchQuery: ['']
  });

  ignoreForm = this.fb.nonNullable.group({
    reason: ['', Validators.required]
  });

  get canOperate(): boolean {
    return this.auth.hasRole('ADMIN', 'ACCOUNTANT');
  }

  get canImport(): boolean {
    return !!this.file && this.importForm.valid;
  }

  filterDateRangeError(): string | null {
    const from = formatIsoDate(this.filterForm.controls.fromDate.value);
    const to = formatIsoDate(this.filterForm.controls.toDate.value);
    return isInvalidDateRange(from, to) ? DATE_RANGE_ERROR : null;
  }

  ngOnInit(): void {
    const now = new Date();
    this.filterForm.patchValue({
      fromDate: new Date(now.getFullYear(), now.getMonth(), 1),
      toDate: new Date(now.getFullYear(), now.getMonth() + 1, 0)
    });
    this.api.list<any>('/api/v1/clients').subscribe((clients) => {
      this.clients = clients;
      const options = clients.map((c: { id: string; name: string }) => ({ id: c.id, name: c.name }));
      this.clientContext.setClients(options);
      this.clientId = this.clientContext.resolveInitial(this.clientContext.clientId(), options);
      this.clientContext.select(this.clientId);
      this.onClientChange();
    });
  }

  onClientChange(): void {
    if (!this.clientId) return;
    this.clientContext.select(this.clientId);
    this.api.get<BankAccount[]>(`/api/v1/clients/${this.clientId}/bank/accounts`, { activeOnly: false }).subscribe({
      next: (accounts) => {
        this.accounts = accounts;
        const firstId = accounts[0]?.id ?? '';
        this.importForm.controls.bankAccountId.setValue(firstId);
        this.filterForm.controls.bankAccountId.setValue(firstId);
        this.reloadReconciliation();
      },
      error: () => this.accounts = []
    });
    this.api.list<CategoryOption>('/api/v1/categories').subscribe((cats) => this.categories = cats);
  }

  onFilterChange(): void {
    this.reloadReconciliation();
  }

  reloadReconciliation(): void {
    if (this.filterDateRangeError()) {
      return;
    }
    const from = formatIsoDate(this.filterForm.controls.fromDate.value);
    const to = formatIsoDate(this.filterForm.controls.toDate.value);
    this.reloadImports();
    this.reloadTransactions();
    if (this.clientId && from && to) {
      this.api.get<ReconciliationSummary>(`/api/v1/clients/${this.clientId}/bank/reconciliation/summary`, {
        bankAccountId: this.filterForm.controls.bankAccountId.value || undefined,
        from,
        to
      }).subscribe({ next: (summary) => this.summary = summary, error: () => this.summary = null });
    }
  }

  reloadImports(): void {
    if (!this.clientId) return;
    this.api.get<BankImportRow[]>(`/api/v1/clients/${this.clientId}/bank/imports`, {
      bankAccountId: this.filterForm.controls.bankAccountId.value || undefined
    }).subscribe({ next: (rows) => this.imports = rows, error: () => this.imports = [] });
  }

  reloadTransactions(): void {
    if (!this.clientId || this.filterDateRangeError()) return;
    const from = formatIsoDate(this.filterForm.controls.fromDate.value);
    const to = formatIsoDate(this.filterForm.controls.toDate.value);
    this.api.get<{ content: BankTransactionRow[] }>(`/api/v1/clients/${this.clientId}/bank/transactions`, {
      bankAccountId: this.filterForm.controls.bankAccountId.value || undefined,
      status: this.filterForm.controls.statusFilter.value || undefined,
      from: from || undefined,
      to: to || undefined,
      q: this.filterForm.controls.searchQuery.value || undefined,
      size: 50
    }).subscribe({
      next: (page) => this.transactions = page.content ?? [],
      error: () => this.transactions = []
    });
  }

  createAccount(): void {
    this.accountForm.markAllAsTouched();
    if (this.accountForm.invalid) {
      this.toast.error('Fix the highlighted fields before saving.');
      return;
    }
    const raw = this.accountForm.getRawValue();
    this.api.post<BankAccount>(`/api/v1/clients/${this.clientId}/bank/accounts`, {
      ...raw,
      currency: raw.currency.toUpperCase()
    }).subscribe({
      next: () => {
        this.accountForm.reset({ bankName: '', accountName: '', maskedAccountNumber: '', currency: 'LKR' });
        this.toast.success('Bank account saved');
        this.onClientChange();
      },
      error: (err) => this.toast.error(err.error?.detail || 'Unable to create account')
    });
  }

  onFileSelected(file: File): void {
    this.file = file;
    this.preview = null;
  }

  mappingParams(): Record<string, string> {
    const raw = this.importForm.getRawValue();
    return {
      bankAccountId: raw.bankAccountId,
      dateColumn: String(raw.dateColumn),
      descriptionColumn: String(raw.descriptionColumn),
      referenceColumn: String(raw.referenceColumn),
      debitColumn: String(raw.debitColumn),
      creditColumn: String(raw.creditColumn),
      balanceColumn: String(raw.balanceColumn),
      dateFormat: 'AUTO',
      headerRow: 'true',
      profileName: raw.profileName || ''
    };
  }

  previewImport(): void {
    this.importForm.markAllAsTouched();
    if (!this.file || this.importForm.invalid) {
      this.toast.error('Select a file and fix mapping fields first.');
      return;
    }
    this.previewing = true;
    this.api.upload<BankImportPreview>(`/api/v1/clients/${this.clientId}/bank/imports/preview`, this.file, this.mappingParams())
      .subscribe({
        next: (preview) => { this.preview = preview; this.previewing = false; },
        error: (err) => { this.toast.error(err.error?.detail || 'Preview failed'); this.previewing = false; }
      });
  }

  runImport(): void {
    this.importForm.markAllAsTouched();
    if (!this.file || this.importForm.invalid) {
      this.toast.error('Select a file and fix mapping fields first.');
      return;
    }
    this.importing = true;
    this.api.upload(`/api/v1/clients/${this.clientId}/bank/imports`, this.file, this.mappingParams())
      .subscribe({
        next: () => {
          this.importing = false;
          this.toast.success('Import completed');
          this.tabIndex = 2;
          this.reloadReconciliation();
        },
        error: (err) => { this.toast.error(err.error?.detail || 'Import failed'); this.importing = false; }
      });
  }

  suggestionLabel(s: MatchSuggestion): string {
    return `${s.ledgerType} · ${s.ledgerLabel} · ${s.amount} · ${s.transactionDate} (${s.confidence})`;
  }

  confirm(row: BankTransactionRow, suggestion: MatchSuggestion): void {
    const body = suggestion.ledgerType === 'EXPENSE'
      ? { expenseId: suggestion.ledgerId }
      : { incomeId: suggestion.ledgerId };
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/confirm`, body)
      .subscribe({ next: () => this.reloadReconciliation(), error: (err) => this.toast.error(err.error?.detail || 'Confirm failed') });
  }

  reject(row: BankTransactionRow, matchId: string): void {
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/reject`, { matchId })
      .subscribe({ next: () => this.reloadReconciliation(), error: (err) => this.toast.error(err.error?.detail || 'Reject failed') });
  }

  unmatch(row: BankTransactionRow): void {
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/unmatch`, {})
      .subscribe({ next: () => this.reloadReconciliation(), error: (err) => this.toast.error(err.error?.detail || 'Unmatch failed') });
  }

  startIgnore(row: BankTransactionRow): void {
    this.ignoringRowId = row.id;
    this.ignoreForm.reset({ reason: '' });
  }

  cancelIgnore(): void {
    this.ignoringRowId = null;
    this.ignoring = false;
    this.ignoreForm.reset({ reason: '' });
  }

  submitIgnore(row: BankTransactionRow): void {
    this.ignoreForm.markAllAsTouched();
    if (this.ignoreForm.invalid) {
      return;
    }
    this.ignoring = true;
    const reason = this.ignoreForm.controls.reason.value.trim();
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/ignore`, { reason })
      .subscribe({
        next: () => { this.cancelIgnore(); this.reloadReconciliation(); },
        error: (err) => { this.toast.error(err.error?.detail || 'Ignore failed'); this.ignoring = false; }
      });
  }

  createExpense(row: BankTransactionRow): void {
    const categoryId = this.categories[0]?.id;
    if (!categoryId) { this.toast.error('Create a category first.'); return; }
    const vendor = row.description?.slice(0, 80) || 'Vendor';
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/create-expense`, {
      categoryId, vendorName: vendor, description: row.description
    }).subscribe({ next: () => this.reloadReconciliation(), error: (err) => this.toast.error(err.error?.detail || 'Create expense failed') });
  }

  createIncome(row: BankTransactionRow): void {
    const categoryId = this.categories[0]?.id;
    if (!categoryId) { this.toast.error('Create a category first.'); return; }
    const payer = row.description?.slice(0, 80) || 'Customer';
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/create-income`, {
      categoryId, payerName: payer, description: row.description
    }).subscribe({ next: () => this.reloadReconciliation(), error: (err) => this.toast.error(err.error?.detail || 'Create income failed') });
  }

  requestDocument(row: BankTransactionRow): void {
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/request-document`, {
      description: `Supporting document for bank line ${row.txnDate} ${row.debit || row.credit}: ${row.description}`,
      documentType: 'RECEIPT'
    }).subscribe({ next: () => this.toast.success('Document request created.'), error: (err) => this.toast.error(err.error?.detail || 'Request failed') });
  }
}
