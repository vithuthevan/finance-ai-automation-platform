import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
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
    FormsModule, RouterLink, MatTableModule, MatFormFieldModule, MatSelectModule,
    MatInputModule, MatButtonModule, MatCardModule, MatTabsModule
  ],
  template: `
    <div class="page">
      <h1>Banking</h1>
      <p class="hint">Import bank CSV statements, match against approved books, and resolve differences before month-end close.</p>

      <div class="toolbar-row">
        <mat-form-field><mat-label>Client</mat-label>
          <mat-select [(ngModel)]="clientId" (selectionChange)="onClientChange()">
            @for (c of clients; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
          </mat-select>
        </mat-form-field>
      </div>
      @if (message) { <p class="hint">{{ message }}</p> }

      <mat-tab-group [(selectedIndex)]="tabIndex">
        <mat-tab label="Accounts">
          @if (canOperate) {
            <section class="card-block">
              <h2>Add bank account</h2>
              <div class="toolbar-row">
                <mat-form-field><mat-label>Bank</mat-label><input matInput [(ngModel)]="newAccount.bankName"></mat-form-field>
                <mat-form-field><mat-label>Account name</mat-label><input matInput [(ngModel)]="newAccount.accountName"></mat-form-field>
                <mat-form-field><mat-label>Account no. (masked)</mat-label><input matInput [(ngModel)]="newAccount.maskedAccountNumber"></mat-form-field>
                <mat-form-field><mat-label>Currency</mat-label><input matInput [(ngModel)]="newAccount.currency"></mat-form-field>
                <button mat-flat-button color="primary" (click)="createAccount()">Save account</button>
              </div>
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
              <div class="toolbar-row">
                <mat-form-field><mat-label>Bank account</mat-label>
                  <mat-select [(ngModel)]="bankAccountId">
                    @for (a of accounts; track a.id) { <mat-option [value]="a.id">{{ a.bankName }} · {{ a.accountName }}</mat-option> }
                  </mat-select>
                </mat-form-field>
                <input type="file" accept=".csv" (change)="onFile($event)">
              </div>
              <h3>Column mapping</h3>
              <div class="toolbar-row">
                <mat-form-field><mat-label>Date col</mat-label><input matInput type="number" [(ngModel)]="mapping.dateColumn"></mat-form-field>
                <mat-form-field><mat-label>Description col</mat-label><input matInput type="number" [(ngModel)]="mapping.descriptionColumn"></mat-form-field>
                <mat-form-field><mat-label>Reference col</mat-label><input matInput type="number" [(ngModel)]="mapping.referenceColumn"></mat-form-field>
                <mat-form-field><mat-label>Debit col</mat-label><input matInput type="number" [(ngModel)]="mapping.debitColumn"></mat-form-field>
                <mat-form-field><mat-label>Credit col</mat-label><input matInput type="number" [(ngModel)]="mapping.creditColumn"></mat-form-field>
                <mat-form-field><mat-label>Balance col</mat-label><input matInput type="number" [(ngModel)]="mapping.balanceColumn"></mat-form-field>
                <mat-form-field><mat-label>Profile name (optional)</mat-label><input matInput [(ngModel)]="profileName"></mat-form-field>
              </div>
              <div class="toolbar-row">
                <button mat-stroked-button (click)="previewImport()" [disabled]="!file || !bankAccountId">Preview</button>
                <button mat-flat-button color="primary" (click)="runImport()" [disabled]="!file || !bankAccountId">Import</button>
              </div>
              @if (preview) {
                <p>Rows: {{ preview.rowsDetected }} · Valid: {{ preview.validRows }} · Invalid: {{ preview.invalidRows }} · Potential duplicates: {{ preview.potentialDuplicates }}</p>
                @if (preview.periodFrom) { <p class="hint">Detected period {{ preview.periodFrom }} – {{ preview.periodTo }}</p> }
                @for (row of preview.sampleRows; track row.lineNumber) {
                  <p>{{ row.txnDate }} · {{ row.description }} · {{ row.debit || row.credit }}</p>
                }
                @for (err of preview.errors; track err.lineNumber) {
                  <p class="hint">Row {{ err.lineNumber }}: {{ err.message }}</p>
                }
              }
            </section>
            <section class="card-block">
              <h2>Recent imports</h2>
              @for (imp of imports; track imp.id) {
                <p>{{ imp.fileName }} · {{ imp.importedCount }} imported · {{ imp.duplicateCount }} duplicates · {{ imp.failedCount }} failed</p>
              }
            </section>
          }
        </mat-tab>

        <mat-tab label="Reconciliation">
          <div class="toolbar-row">
            <mat-form-field><mat-label>Bank account</mat-label>
              <mat-select [(ngModel)]="bankAccountId" (selectionChange)="reloadReconciliation()">
                <mat-option value="">All accounts</mat-option>
                @for (a of accounts; track a.id) { <mat-option [value]="a.id">{{ a.bankName }} · {{ a.accountName }}</mat-option> }
              </mat-select>
            </mat-form-field>
            <mat-form-field><mat-label>Status</mat-label>
              <mat-select [(ngModel)]="statusFilter" (selectionChange)="reloadTransactions()">
                <mat-option value="">All</mat-option>
                <mat-option value="UNMATCHED">Unmatched</mat-option>
                <mat-option value="SUGGESTED">Suggested</mat-option>
                <mat-option value="MATCHED">Matched</mat-option>
                <mat-option value="PENDING_APPROVAL">Pending approval</mat-option>
                <mat-option value="IGNORED">Ignored</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field><mat-label>From</mat-label><input matInput type="date" [(ngModel)]="fromDate" (change)="reloadReconciliation()"></mat-form-field>
            <mat-form-field><mat-label>To</mat-label><input matInput type="date" [(ngModel)]="toDate" (change)="reloadReconciliation()"></mat-form-field>
            <mat-form-field><mat-label>Search</mat-label><input matInput [(ngModel)]="searchQuery" (keyup.enter)="reloadTransactions()"></mat-form-field>
            <button mat-stroked-button (click)="reloadReconciliation()">Refresh</button>
          </div>

          @if (summary) {
            <div class="grid-2">
              <mat-card class="metric-card"><div class="label">Reconciliation</div><div class="value">{{ summary.reconciliationPercent }}%</div></mat-card>
              <mat-card class="metric-card"><div class="label">Matched</div><div class="value">{{ summary.matched }}</div></mat-card>
              <mat-card class="metric-card"><div class="label">Suggested</div><div class="value">{{ summary.suggested }}</div></mat-card>
              <mat-card class="metric-card"><div class="label">Unmatched</div><div class="value">{{ summary.unmatched }}</div></mat-card>
              <mat-card class="metric-card"><div class="label">Ignored</div><div class="value">{{ summary.ignored }}</div></mat-card>
              <mat-card class="metric-card"><div class="label">Pending approval</div><div class="value">{{ summary.pendingApproval }}</div></mat-card>
            </div>
          }

          @for (row of transactions; track row.id) {
            <mat-card class="metric-card reconcile-card">
              <div class="toolbar-row">
                <strong>{{ row.txnDate }}</strong>
                <span>{{ row.description }}</span>
                <span>{{ row.debit ? ('-' + row.debit) : ('+' + row.credit) }}</span>
                <span [class]="'status-' + row.matchStatus">{{ row.matchStatus }}</span>
              </div>
              @if (row.suggestions?.length) {
                <p class="hint">Suggested match:</p>
                @for (s of row.suggestions; track s.ledgerId) {
                  <div class="toolbar-row">
                    <span>{{ s.ledgerType }} · {{ s.ledgerLabel }} · {{ s.amount }} · {{ s.transactionDate }} ({{ s.confidence }})</span>
                    @if (canOperate && row.matchStatus !== 'MATCHED' && row.matchStatus !== 'IGNORED') {
                      <button mat-flat-button color="primary" (click)="confirm(row, s)">Confirm</button>
                      @if (s.matchId) { <button mat-button (click)="reject(row, s.matchId!)">Reject</button> }
                    }
                  </div>
                }
              }
              @if (canOperate && row.matchStatus !== 'MATCHED' && row.matchStatus !== 'IGNORED') {
                <div class="toolbar-row">
                  @if (row.debit) {
                    <button mat-stroked-button (click)="createExpense(row)">Create expense (draft)</button>
                  }
                  @if (row.credit) {
                    <button mat-stroked-button (click)="createIncome(row)">Create income (draft)</button>
                  }
                  <button mat-stroked-button (click)="requestDocument(row)">Request document</button>
                  <button mat-button (click)="ignoreRow(row)">Ignore</button>
                </div>
              }
              @if (canOperate && row.matchStatus === 'MATCHED') {
                <button mat-button (click)="unmatch(row)">Unmatch</button>
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
  `
})
export class BankingPage implements OnInit {
  clients: any[] = [];
  accounts: BankAccount[] = [];
  imports: BankImportRow[] = [];
  transactions: BankTransactionRow[] = [];
  categories: CategoryOption[] = [];
  preview: BankImportPreview | null = null;
  summary: ReconciliationSummary | null = null;
  clientId = '';
  bankAccountId = '';
  statusFilter = '';
  fromDate = '';
  toDate = '';
  searchQuery = '';
  file: File | null = null;
  profileName = '';
  message = '';
  tabIndex = 0;
  accountColumns = ['bank', 'name', 'number', 'currency', 'status'];
  mapping = { dateColumn: 0, descriptionColumn: 1, referenceColumn: 2, debitColumn: 3, creditColumn: 4, balanceColumn: 5 };
  newAccount = { bankName: '', accountName: '', maskedAccountNumber: '', currency: 'LKR' };

  constructor(private api: ApiService, readonly auth: AuthService) {}

  get canOperate(): boolean {
    return this.auth.hasRole('ADMIN', 'ACCOUNTANT');
  }

  ngOnInit(): void {
    const now = new Date();
    this.fromDate = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10);
    this.toDate = new Date(now.getFullYear(), now.getMonth() + 1, 0).toISOString().slice(0, 10);
    this.api.list<any>('/api/v1/clients').subscribe((clients) => {
      this.clients = clients;
      this.clientId = clients[0]?.id ?? '';
      this.onClientChange();
    });
  }

  onClientChange(): void {
    if (!this.clientId) return;
    this.api.get<BankAccount[]>(`/api/v1/clients/${this.clientId}/bank/accounts`, { activeOnly: false }).subscribe({
      next: (accounts) => {
        this.accounts = accounts;
        this.bankAccountId = accounts[0]?.id ?? '';
        this.reloadReconciliation();
      },
      error: () => this.accounts = []
    });
    this.api.list<CategoryOption>('/api/v1/categories').subscribe((cats) => this.categories = cats);
  }

  reloadReconciliation(): void {
    this.reloadImports();
    this.reloadTransactions();
    if (this.clientId && this.fromDate && this.toDate) {
      this.api.get<ReconciliationSummary>(`/api/v1/clients/${this.clientId}/bank/reconciliation/summary`, {
        bankAccountId: this.bankAccountId || undefined,
        from: this.fromDate,
        to: this.toDate
      }).subscribe({ next: (summary) => this.summary = summary, error: () => this.summary = null });
    }
  }

  reloadImports(): void {
    if (!this.clientId) return;
    this.api.get<BankImportRow[]>(`/api/v1/clients/${this.clientId}/bank/imports`, {
      bankAccountId: this.bankAccountId || undefined
    }).subscribe({ next: (rows) => this.imports = rows, error: () => this.imports = [] });
  }

  reloadTransactions(): void {
    if (!this.clientId) return;
    this.api.get<{ content: BankTransactionRow[] }>(`/api/v1/clients/${this.clientId}/bank/transactions`, {
      bankAccountId: this.bankAccountId || undefined,
      status: this.statusFilter || undefined,
      from: this.fromDate || undefined,
      to: this.toDate || undefined,
      q: this.searchQuery || undefined,
      size: 50
    }).subscribe({
      next: (page) => this.transactions = page.content ?? [],
      error: () => this.transactions = []
    });
  }

  createAccount(): void {
    this.api.post<BankAccount>(`/api/v1/clients/${this.clientId}/bank/accounts`, this.newAccount).subscribe({
      next: () => {
        this.newAccount = { bankName: '', accountName: '', maskedAccountNumber: '', currency: 'LKR' };
        this.onClientChange();
      },
      error: (err) => this.message = err.error?.detail || 'Unable to create account'
    });
  }

  onFile(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.preview = null;
  }

  mappingParams(): Record<string, string> {
    return {
      bankAccountId: this.bankAccountId,
      dateColumn: String(this.mapping.dateColumn),
      descriptionColumn: String(this.mapping.descriptionColumn),
      referenceColumn: String(this.mapping.referenceColumn),
      debitColumn: String(this.mapping.debitColumn),
      creditColumn: String(this.mapping.creditColumn),
      balanceColumn: String(this.mapping.balanceColumn),
      dateFormat: 'AUTO',
      headerRow: 'true',
      profileName: this.profileName || ''
    };
  }

  previewImport(): void {
    if (!this.file || !this.bankAccountId) return;
    this.api.upload<BankImportPreview>(`/api/v1/clients/${this.clientId}/bank/imports/preview`, this.file, this.mappingParams())
      .subscribe({ next: (preview) => this.preview = preview, error: (err) => this.message = err.error?.detail || 'Preview failed' });
  }

  runImport(): void {
    if (!this.file || !this.bankAccountId) return;
    this.api.upload(`/api/v1/clients/${this.clientId}/bank/imports`, this.file, this.mappingParams())
      .subscribe({
        next: () => { this.tabIndex = 2; this.reloadReconciliation(); },
        error: (err) => this.message = err.error?.detail || 'Import failed'
      });
  }

  confirm(row: BankTransactionRow, suggestion: MatchSuggestion): void {
    const body = suggestion.ledgerType === 'EXPENSE'
      ? { expenseId: suggestion.ledgerId }
      : { incomeId: suggestion.ledgerId };
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/confirm`, body)
      .subscribe({ next: () => this.reloadReconciliation(), error: (err) => this.message = err.error?.detail || 'Confirm failed' });
  }

  reject(row: BankTransactionRow, matchId: string): void {
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/reject`, { matchId })
      .subscribe({ next: () => this.reloadReconciliation(), error: (err) => this.message = err.error?.detail || 'Reject failed' });
  }

  unmatch(row: BankTransactionRow): void {
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/unmatch`, {})
      .subscribe({ next: () => this.reloadReconciliation(), error: (err) => this.message = err.error?.detail || 'Unmatch failed' });
  }

  ignoreRow(row: BankTransactionRow): void {
    const reason = prompt('Reason for ignoring this bank line (required):');
    if (!reason?.trim()) return;
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/ignore`, { reason: reason.trim() })
      .subscribe({ next: () => this.reloadReconciliation(), error: (err) => this.message = err.error?.detail || 'Ignore failed' });
  }

  createExpense(row: BankTransactionRow): void {
    const categoryId = this.categories[0]?.id;
    if (!categoryId) { this.message = 'Create a category first.'; return; }
    const vendor = row.description?.slice(0, 80) || 'Vendor';
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/create-expense`, {
      categoryId, vendorName: vendor, description: row.description
    }).subscribe({ next: () => this.reloadReconciliation(), error: (err) => this.message = err.error?.detail || 'Create expense failed' });
  }

  createIncome(row: BankTransactionRow): void {
    const categoryId = this.categories[0]?.id;
    if (!categoryId) { this.message = 'Create a category first.'; return; }
    const payer = row.description?.slice(0, 80) || 'Customer';
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/create-income`, {
      categoryId, payerName: payer, description: row.description
    }).subscribe({ next: () => this.reloadReconciliation(), error: (err) => this.message = err.error?.detail || 'Create income failed' });
  }

  requestDocument(row: BankTransactionRow): void {
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/request-document`, {
      description: `Supporting document for bank line ${row.txnDate} ${row.debit || row.credit}: ${row.description}`,
      documentType: 'RECEIPT'
    }).subscribe({ next: () => this.message = 'Document request created.', error: (err) => this.message = err.error?.detail || 'Request failed' });
  }
}
