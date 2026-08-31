import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCardModule],
  template: `
    <div class="page">
      <p><a routerLink="/app/documents">Back to inbox</a></p>
      <h1>Document review</h1>
      @if (doc) {
        <div class="doc-review">
          <section class="preview-pane">
            @if (previewUrl && isImage) {
              <img [src]="previewUrl" [alt]="doc.fileName" class="preview-image">
            } @else if (safePreviewUrl && isPdf) {
              <iframe [src]="safePreviewUrl" class="preview-frame" title="Document preview"></iframe>
            } @else {
              <p>Preview is not available for this file type. Use download.</p>
            }
          </section>
          <section>
            <mat-card class="metric-card">
              <div class="label">{{ doc.fileName }}</div>
              <p>{{ doc.clientName }} · {{ doc.documentType }} · <span [class]="'status-' + doc.status">{{ doc.status }}</span></p>
              <p>Uploaded by {{ doc.uploadedByName }} on {{ doc.uploadedAt | date:'medium' }}</p>
              @if (doc.reviewOutcome) { <p>Review outcome: {{ doc.reviewOutcome }}</p> }
              @if (doc.reviewNote) { <p>Review: {{ doc.reviewNote }}</p> }
              <button mat-stroked-button (click)="download()">Download</button>
            </mat-card>

            @if (doc.status === 'PROCESSING') {
              <p class="hint">Automatic extraction is running. Refresh shortly.</p>
            }

            @if (doc.status === 'FAILED' || doc.failureCode) {
              <p class="empty-report">{{ doc.failureMessage || 'Automatic extraction failed. You can retry processing or enter the transaction manually.' }}</p>
              @if (auth.canMutateDocuments()) {
                <button mat-stroked-button (click)="retry()">Retry processing</button>
              }
            }

            <h2>Extracted facts</h2>
            <table class="pnl-table">
              <tr><td>Vendor / customer</td><td [class.low-confidence]="low(doc.supplierConfidence)">{{ doc.suggestedVendorOrCustomer || '—' }} <span class="hint">{{ band(doc.supplierConfidence) }}</span></td></tr>
              <tr><td>Date</td><td [class.low-confidence]="low(doc.dateConfidence)">{{ doc.suggestedDate || '—' }} <span class="hint">{{ band(doc.dateConfidence) }}</span></td></tr>
              <tr><td>Amount</td><td [class.low-confidence]="low(doc.amountConfidence)">{{ doc.suggestedAmount || '—' }} {{ doc.suggestedCurrency }} <span class="hint">{{ band(doc.amountConfidence) }}</span></td></tr>
              <tr><td>Tax</td><td [class.low-confidence]="low(doc.taxConfidence)">{{ doc.suggestedTaxAmount || '—' }} <span class="hint">{{ band(doc.taxConfidence) }}</span></td></tr>
              <tr><td>Invoice / receipt no.</td><td>{{ doc.suggestedInvoiceNo || '—' }}</td></tr>
              <tr><td>Overall confidence</td><td>{{ doc.confidenceLabel || 'Unknown' }}</td></tr>
            </table>
            @if (doc.amountInconsistency) { <p class="hint">AMOUNT_INCONSISTENCY: subtotal + tax does not match total.</p> }
            @if (doc.dateWarning) { <p class="hint">Date flag: {{ doc.dateWarning }}</p> }
            <p class="hint">Confidence is a provider hint, not a guarantee.</p>

            @if (auth.canMutateDocuments() && doc.status !== 'REJECTED' && doc.status !== 'LINKED') {
              <h2>Accounting suggestion</h2>
              <div class="toolbar-row">
                <mat-form-field>
                  <mat-label>Type</mat-label>
                  <mat-select [(ngModel)]="draft.transactionType">
                    <mat-option value="EXPENSE">Expense</mat-option>
                    <mat-option value="INCOME">Income</mat-option>
                  </mat-select>
                </mat-form-field>
                <mat-form-field>
                  <mat-label>Date</mat-label>
                  <input matInput type="date" [(ngModel)]="draft.transactionDate">
                </mat-form-field>
                <mat-form-field>
                  <mat-label>Category</mat-label>
                  <mat-select [(ngModel)]="draft.categoryId">
                    @for (cat of categories; track cat.id) { <mat-option [value]="cat.id">{{ cat.code }} {{ cat.name }}</mat-option> }
                  </mat-select>
                </mat-form-field>
                <mat-form-field>
                  <mat-label>Amount</mat-label>
                  <input matInput type="number" [(ngModel)]="draft.amount">
                </mat-form-field>
                <mat-form-field>
                  <mat-label>Tax</mat-label>
                  <input matInput type="number" [(ngModel)]="draft.taxAmount">
                </mat-form-field>
                <mat-form-field>
                  <mat-label>Party</mat-label>
                  <input matInput [(ngModel)]="draft.partyName">
                </mat-form-field>
                <mat-form-field>
                  <mat-label>Description</mat-label>
                  <input matInput [(ngModel)]="draft.description">
                </mat-form-field>
                @if (draft.transactionType === 'INCOME') {
                  <mat-form-field>
                    <mat-label>Payment method</mat-label>
                    <mat-select [(ngModel)]="draft.paymentMethod">
                      <mat-option value="BANK_TRANSFER">Bank transfer</mat-option>
                      <mat-option value="CASH">Cash</mat-option>
                      <mat-option value="CARD">Card</mat-option>
                    </mat-select>
                  </mat-form-field>
                }
              </div>
              <div class="toolbar-row">
                <button mat-flat-button color="primary" (click)="accept()">Accept as draft</button>
                <button mat-stroked-button (click)="rejectSuggestion()">Reject suggestion</button>
                <button mat-stroked-button (click)="createDraft()">Create draft manually</button>
              </div>
              <p class="hint">Accept creates a DRAFT only. Approval remains a separate step.</p>
            } @else if (auth.hasRole('AUDITOR')) {
              <p class="hint">Auditor access is read-only. AI suggestions cannot be changed.</p>
            } @else if (auth.hasRole('BUSINESS_OWNER')) {
              <p class="hint">Your accountant reviews extraction and creates the draft.</p>
            }

            @if (auth.canMutateDocuments() && doc.status !== 'REJECTED') {
              <h2>Link existing transaction</h2>
              <div class="toolbar-row">
                <mat-form-field>
                  <mat-label>Expense</mat-label>
                  <mat-select [(ngModel)]="linkExpenseId">
                    <mat-option value="">None</mat-option>
                    @for (row of expenses; track row.id) {
                      <mat-option [value]="row.id">{{ row.transactionDate }} · {{ row.vendorName }} · {{ row.amount }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <button mat-stroked-button (click)="linkExpense()" [disabled]="!linkExpenseId">Link expense</button>
                <mat-form-field>
                  <mat-label>Income</mat-label>
                  <mat-select [(ngModel)]="linkIncomeId">
                    <mat-option value="">None</mat-option>
                    @for (row of incomes; track row.id) {
                      <mat-option [value]="row.id">{{ row.transactionDate }} · {{ row.customerName }} · {{ row.amount }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <button mat-stroked-button (click)="linkIncome()" [disabled]="!linkIncomeId">Link income</button>
              </div>
              <h2>Reject document</h2>
              <div class="toolbar-row">
                <mat-form-field>
                  <mat-label>Reason</mat-label>
                  <input matInput [(ngModel)]="rejectReason">
                </mat-form-field>
                <button mat-stroked-button color="warn" (click)="reject()">Reject document</button>
              </div>
            }

            @if (message) { <p class="hint">{{ message }}</p> }
          </section>
        </div>
      }
    </div>
  `
})
export class DocumentReviewPage implements OnInit, OnDestroy {
  clientId = '';
  documentId = '';
  doc: any;
  previewUrl = '';
  safePreviewUrl: SafeResourceUrl | null = null;
  isImage = false;
  isPdf = false;
  categories: any[] = [];
  expenses: any[] = [];
  incomes: any[] = [];
  linkExpenseId = '';
  linkIncomeId = '';
  rejectReason = '';
  message = '';
  draft = {
    transactionType: 'EXPENSE',
    transactionDate: '',
    categoryId: '',
    amount: 0,
    taxAmount: 0,
    partyName: '',
    description: '',
    paymentMethod: 'BANK_TRANSFER',
    currencyCode: 'LKR',
    referenceNo: ''
  };

  constructor(
    private route: ActivatedRoute,
    private api: ApiService,
    readonly auth: AuthService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    this.clientId = this.route.snapshot.paramMap.get('clientId') ?? '';
    this.documentId = this.route.snapshot.paramMap.get('documentId') ?? '';
    this.reload();
  }

  ngOnDestroy(): void {
    if (this.previewUrl) {
      URL.revokeObjectURL(this.previewUrl);
    }
  }

  reload(): void {
    this.api.get<any>(`/api/v1/clients/${this.clientId}/documents/${this.documentId}`).subscribe((doc) => {
      this.doc = doc;
      this.applySuggestion(doc);
      this.loadPreview(doc);
      this.api.get<any[]>(`/api/v1/categories`).subscribe((cats) => this.categories = cats);
      this.api.get<any>(`/api/v1/clients/${this.clientId}/expenses`, { size: 50 }).subscribe({
        next: (page) => this.expenses = (page.content ?? []).filter((row: any) => row.status !== 'VOID'),
        error: () => this.expenses = []
      });
      this.api.get<any>(`/api/v1/clients/${this.clientId}/income`, { size: 50 }).subscribe({
        next: (page) => this.incomes = (page.content ?? []).filter((row: any) => row.status !== 'VOID'),
        error: () => this.incomes = []
      });
    });
  }

  accept(): void {
    this.api.post(`/api/v1/clients/${this.clientId}/documents/${this.documentId}/review/accept`, this.draft).subscribe({
      next: () => {
        this.message = 'Draft created from suggestion. It is not approved.';
        this.reload();
      },
      error: (err) => this.message = err.error?.detail || 'Unable to accept suggestion'
    });
  }

  rejectSuggestion(): void {
    this.api.post(`/api/v1/clients/${this.clientId}/documents/${this.documentId}/review/reject-suggestion`, {
      note: 'Suggestion rejected'
    }).subscribe({
      next: () => {
        this.message = 'AI suggestion rejected. The document remains available for a manual draft.';
        this.reload();
      },
      error: (err) => this.message = err.error?.detail || 'Unable to reject suggestion'
    });
  }

  createDraft(): void {
    this.api.post(`/api/v1/clients/${this.clientId}/documents/${this.documentId}/transactions`, this.draft).subscribe({
      next: () => {
        this.message = 'Manual draft created. Approval is a separate step.';
        this.reload();
      },
      error: (err) => this.message = err.error?.detail || 'Unable to create transaction'
    });
  }

  retry(): void {
    this.api.post(`/api/v1/clients/${this.clientId}/documents/${this.documentId}/retry-processing`).subscribe({
      next: () => {
        this.message = 'Processing queued.';
        this.reload();
      },
      error: (err) => this.message = err.error?.detail || 'Unable to retry'
    });
  }

  linkExpense(): void {
    this.api.post(`/api/v1/clients/${this.clientId}/expenses/${this.linkExpenseId}/documents`, { documentId: this.documentId }).subscribe({
      next: () => { this.message = 'Document linked to expense.'; this.reload(); },
      error: (err) => this.message = err.error?.detail || 'Unable to link expense'
    });
  }

  linkIncome(): void {
    this.api.post(`/api/v1/clients/${this.clientId}/income/${this.linkIncomeId}/documents`, { documentId: this.documentId }).subscribe({
      next: () => { this.message = 'Document linked to income.'; this.reload(); },
      error: (err) => this.message = err.error?.detail || 'Unable to link income'
    });
  }

  reject(): void {
    if (!this.rejectReason.trim()) {
      this.message = 'A rejection reason is required.';
      return;
    }
    this.api.post(`/api/v1/clients/${this.clientId}/documents/${this.documentId}/reject`, { reason: this.rejectReason }).subscribe({
      next: () => { this.message = 'Document rejected.'; this.reload(); },
      error: (err) => this.message = err.error?.detail || 'Unable to reject document'
    });
  }

  download(): void {
    this.api.download(`/api/v1/clients/${this.clientId}/documents/${this.documentId}/content`).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = this.doc?.fileName || 'document';
      anchor.click();
      URL.revokeObjectURL(url);
    });
  }

  band(value: unknown): string {
    if (value === null || value === undefined || value === '') {
      return 'Unknown';
    }
    const number = Number(value);
    if (number >= 0.8) {
      return 'High';
    }
    if (number >= 0.5) {
      return 'Medium';
    }
    return 'Low';
  }

  low(value: unknown): boolean {
    return value !== null && value !== undefined && value !== '' && Number(value) < 0.5;
  }

  private applySuggestion(doc: any): void {
    this.draft = {
      transactionType: doc.suggestedType && doc.suggestedType !== 'UNKNOWN' ? doc.suggestedType : 'EXPENSE',
      transactionDate: doc.suggestedDate || '',
      categoryId: doc.suggestedCategoryId || '',
      amount: Number(doc.suggestedAmount || 0),
      taxAmount: Number(doc.suggestedTaxAmount || 0),
      partyName: doc.suggestedVendorOrCustomer || '',
      description: doc.suggestedDescription || '',
      paymentMethod: doc.suggestedPaymentMethod || 'BANK_TRANSFER',
      currencyCode: doc.suggestedCurrency || 'LKR',
      referenceNo: doc.suggestedInvoiceNo || ''
    };
  }

  private loadPreview(doc: any): void {
    this.isPdf = doc.mimeType === 'application/pdf';
    this.isImage = ['image/jpeg', 'image/png', 'image/webp'].includes(doc.mimeType);
    if (!this.isPdf && !this.isImage) {
      return;
    }
    this.api.download(`/api/v1/clients/${this.clientId}/documents/${this.documentId}/content`).subscribe((blob) => {
      if (this.previewUrl) {
        URL.revokeObjectURL(this.previewUrl);
      }
      this.previewUrl = URL.createObjectURL(blob);
      this.safePreviewUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.previewUrl);
    });
  }
}
