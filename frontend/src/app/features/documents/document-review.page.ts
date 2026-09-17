import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { NgxExtendedPdfViewerModule } from 'ngx-extended-pdf-viewer';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../shared/toast.service';
import { formatIsoDate, parseIsoDate } from '../../shared/date.util';
import { amountValidators, currencyCodeValidators } from '../../shared/form.validators';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';

@Component({
  standalone: true,
  imports: [
    ReactiveFormsModule, DatePipe, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatCardModule, MatDatepickerModule, NgxExtendedPdfViewerModule,
    PageHeaderComponent, StatusBadgeComponent, LoadingStateComponent, ErrorStateComponent
  ],
  template: `
    <div class="page doc-review-page">
      <app-page-header
        title="Document review"
        subtitle="Compare the source file with extracted facts, then create or link the accounting record."
        backLink="/app/documents"
        backLabel="← Document inbox" />

      @if (loadError) {
        <app-error-state title="Could not open document" [message]="loadError" (retry)="reload()" />
      } @else if (loading) {
        <app-loading-state message="Loading document…" />
      } @else if (doc) {
        <div class="doc-review">
          <section class="preview-pane" aria-label="Source document">
            <h2 class="section-label">Source document</h2>
            @if (previewUrl && isImage) {
              <img [src]="previewUrl" [alt]="doc.fileName" class="preview-image">
            } @else if (previewUrl && isPdf) {
              <ngx-extended-pdf-viewer
                [src]="previewUrl"
                [height]="'100%'"
                [showToolbar]="true"
                [textLayer]="true">
              </ngx-extended-pdf-viewer>
            } @else {
              <p class="preview-fallback">Preview is not available for this file type. Use download.</p>
            }
          </section>

          <section class="review-panel">
            <mat-card class="metric-card doc-meta">
              <div class="file-name" [title]="doc.fileName">{{ doc.fileName }}</div>
              <p class="meta-line meta-badges">
                <span>{{ doc.clientName }}</span>
                <span class="sep">·</span>
                <span>{{ doc.documentType }}</span>
                <app-status-badge [status]="doc.status" />
              </p>
              <p class="meta-line muted">Uploaded by {{ doc.uploadedByName }} on {{ doc.uploadedAt | date:'medium' }}</p>
              @if (doc.reviewOutcome) { <p class="meta-line muted">Review outcome: {{ doc.reviewOutcome }}</p> }
              @if (doc.reviewNote) { <p class="meta-line muted">Review: {{ doc.reviewNote }}</p> }
              <button mat-stroked-button type="button" (click)="download()">Download</button>
            </mat-card>

            @if (doc.status === 'PROCESSING') {
              <p class="hint">Automatic extraction is running. Refresh shortly.</p>
            }

            @if (doc.status === 'FAILED' || doc.failureCode) {
              <div class="alert-banner">
                <p>{{ doc.failureMessage || 'Automatic extraction failed. You can retry processing or enter the transaction manually.' }}</p>
                @if (auth.canMutateDocuments()) {
                  <button mat-stroked-button type="button" (click)="retry()">Retry processing</button>
                }
              </div>
            }

            <div class="facts-card" aria-label="Extracted information">
              <h2>Extracted information</h2>
              <dl class="facts-list">
                <div class="fact-row" [class.low-confidence]="low(doc.supplierConfidence)">
                  <dt>Vendor / customer</dt>
                  <dd>
                    <span>{{ displayValue(doc.suggestedVendorOrCustomer) }}</span>
                    <span class="confidence">{{ band(doc.supplierConfidence) }}</span>
                  </dd>
                </div>
                <div class="fact-row" [class.low-confidence]="low(doc.dateConfidence)">
                  <dt>Date</dt>
                  <dd>
                    <span>{{ displayValue(doc.suggestedDate) }}</span>
                    <span class="confidence">{{ band(doc.dateConfidence) }}</span>
                  </dd>
                </div>
                <div class="fact-row" [class.low-confidence]="low(doc.amountConfidence)">
                  <dt>Amount</dt>
                  <dd>
                    <span>{{ amountDisplay() }}</span>
                    <span class="confidence">{{ band(doc.amountConfidence) }}</span>
                  </dd>
                </div>
                <div class="fact-row" [class.low-confidence]="low(doc.taxConfidence)">
                  <dt>Tax</dt>
                  <dd>
                    <span>{{ displayValue(doc.suggestedTaxAmount) }}</span>
                    <span class="confidence">{{ band(doc.taxConfidence) }}</span>
                  </dd>
                </div>
                <div class="fact-row">
                  <dt>Invoice / receipt no.</dt>
                  <dd>{{ displayValue(doc.suggestedInvoiceNo) }}</dd>
                </div>
                <div class="fact-row">
                  <dt>Overall confidence</dt>
                  <dd>{{ doc.confidenceLabel || 'Unknown' }}</dd>
                </div>
              </dl>
              @if (doc.amountInconsistency) {
                <p class="hint warn">AMOUNT_INCONSISTENCY: subtotal + tax does not match total.</p>
              }
              @if (doc.dateWarning) {
                <p class="hint warn">Date flag: {{ doc.dateWarning }}</p>
              }
              <p class="hint">Confidence is a provider hint, not a guarantee.</p>
            </div>

            @if (auth.canMutateDocuments() && doc.status !== 'REJECTED' && doc.status !== 'LINKED') {
              <div class="form-card" aria-label="Accounting record">
                <h2>{{ draftHeading() }}</h2>
                <p class="hint">Creates a draft ledger entry from the facts above. Workflow is unchanged.</p>
                <form [formGroup]="draftForm" (ngSubmit)="createDraft()" novalidate>
                  <div class="form-grid">
                    <mat-form-field appearance="outline">
                      <mat-label>Type</mat-label>
                      <mat-select formControlName="transactionType" (selectionChange)="onTypeChange()">
                        <mat-option value="EXPENSE">Expense</mat-option>
                        <mat-option value="INCOME">Income</mat-option>
                      </mat-select>
                      @if (draftForm.controls.transactionType.touched && draftForm.controls.transactionType.invalid) {
                        <mat-error>Type is required</mat-error>
                      }
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Date</mat-label>
                      <input matInput [matDatepicker]="draftDatePicker" formControlName="transactionDate">
                      <mat-datepicker-toggle matIconSuffix [for]="draftDatePicker"></mat-datepicker-toggle>
                      <mat-datepicker #draftDatePicker></mat-datepicker>
                      @if (draftForm.controls.transactionDate.touched && draftForm.controls.transactionDate.invalid) {
                        <mat-error>Date is required</mat-error>
                      }
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Category</mat-label>
                      <mat-select formControlName="categoryId">
                        @for (cat of filteredCategories; track cat.id) {
                          <mat-option [value]="cat.id">{{ cat.code }} {{ cat.name }}</mat-option>
                        }
                      </mat-select>
                      @if (draftForm.controls.categoryId.touched && draftForm.controls.categoryId.invalid) {
                        <mat-error>Category is required</mat-error>
                      }
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Amount</mat-label>
                      <input matInput type="number" step="0.01" min="0.01" formControlName="amount">
                      @if (draftForm.controls.amount.touched && draftForm.controls.amount.hasError('required')) {
                        <mat-error>Amount is required</mat-error>
                      } @else if (draftForm.controls.amount.touched && draftForm.controls.amount.hasError('min')) {
                        <mat-error>Amount must be at least 0.01</mat-error>
                      }
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Tax</mat-label>
                      <input matInput type="number" step="0.01" min="0" formControlName="taxAmount">
                      @if (draftForm.controls.taxAmount.touched && draftForm.controls.taxAmount.hasError('min')) {
                        <mat-error>Tax cannot be negative</mat-error>
                      }
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Currency</mat-label>
                      <input matInput formControlName="currencyCode" maxlength="3">
                      @if (draftForm.controls.currencyCode.touched && draftForm.controls.currencyCode.invalid) {
                        <mat-error>Use a 3-letter currency code</mat-error>
                      }
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Party</mat-label>
                      <input matInput formControlName="partyName">
                      @if (draftForm.controls.partyName.touched && draftForm.controls.partyName.hasError('required')) {
                        <mat-error>Vendor or customer is required</mat-error>
                      } @else if (draftForm.controls.partyName.touched && draftForm.controls.partyName.hasError('maxlength')) {
                        <mat-error>Must be 200 characters or fewer</mat-error>
                      }
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Reference no.</mat-label>
                      <input matInput formControlName="referenceNo">
                    </mat-form-field>

                    <mat-form-field appearance="outline" class="span-2">
                      <mat-label>Description</mat-label>
                      <input matInput formControlName="description">
                    </mat-form-field>

                    @if (draftForm.controls.transactionType.value === 'INCOME') {
                      <mat-form-field appearance="outline">
                        <mat-label>Payment method</mat-label>
                        <mat-select formControlName="paymentMethod">
                          <mat-option value="BANK_TRANSFER">Bank transfer</mat-option>
                          <mat-option value="CASH">Cash</mat-option>
                          <mat-option value="CARD">Card</mat-option>
                        </mat-select>
                        @if (draftForm.controls.paymentMethod.touched && draftForm.controls.paymentMethod.invalid) {
                          <mat-error>Payment method is required</mat-error>
                        }
                      </mat-form-field>
                    }
                  </div>

                  <div class="actions-row">
                    <button mat-flat-button color="primary" type="button" (click)="accept()" [disabled]="draftForm.invalid || busy">
                      {{ busy ? 'Saving…' : 'Accept as draft' }}
                    </button>
                    <button mat-stroked-button type="button" (click)="rejectSuggestion()" [disabled]="busy">
                      Reject suggestion
                    </button>
                    <button mat-stroked-button color="primary" type="submit" [disabled]="draftForm.invalid || busy">
                      {{ busy ? 'Saving…' : 'Create draft manually' }}
                    </button>
                  </div>
                  <p class="hint">Accept and create draft save a DRAFT only. Approval remains a separate step.</p>
                </form>
              </div>
            } @else if (auth.hasRole('AUDITOR')) {
              <p class="hint">Auditor access is read-only. AI suggestions cannot be changed.</p>
            } @else if (auth.hasRole('BUSINESS_OWNER')) {
              <p class="hint">Your accountant reviews extraction and creates the draft.</p>
            }

            @if (auth.canMutateDocuments() && doc.status !== 'REJECTED') {
              <div class="form-card">
                <h2>Link existing transaction</h2>
                <form class="form-grid" [formGroup]="linkForm">
                  <mat-form-field appearance="outline">
                    <mat-label>Expense</mat-label>
                    <mat-select formControlName="expenseId">
                      <mat-option value="">None</mat-option>
                      @for (row of expenses; track row.id) {
                        <mat-option [value]="row.id">{{ row.transactionDate }} · {{ row.vendorName }} · {{ row.amount }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>
                  <button mat-stroked-button type="button" class="align-field" (click)="linkExpense()" [disabled]="!linkForm.value.expenseId || busy">
                    Link expense
                  </button>
                  <mat-form-field appearance="outline">
                    <mat-label>Income</mat-label>
                    <mat-select formControlName="incomeId">
                      <mat-option value="">None</mat-option>
                      @for (row of incomes; track row.id) {
                        <mat-option [value]="row.id">{{ row.transactionDate }} · {{ row.customerName }} · {{ row.amount }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>
                  <button mat-stroked-button type="button" class="align-field" (click)="linkIncome()" [disabled]="!linkForm.value.incomeId || busy">
                    Link income
                  </button>
                </form>
              </div>

              <div class="form-card">
                <h2>Reject document</h2>
                <form class="reject-row" [formGroup]="rejectForm" (ngSubmit)="reject()">
                  <mat-form-field appearance="outline" class="full-width">
                    <mat-label>Reason</mat-label>
                    <input matInput formControlName="reason">
                    @if (rejectForm.controls.reason.touched && rejectForm.controls.reason.hasError('required')) {
                      <mat-error>A rejection reason is required</mat-error>
                    } @else if (rejectForm.controls.reason.touched && rejectForm.controls.reason.hasError('minlength')) {
                      <mat-error>Reason must be at least 3 characters</mat-error>
                    }
                  </mat-form-field>
                  <button mat-stroked-button color="warn" type="submit" [disabled]="rejectForm.invalid || busy">
                    {{ busy ? 'Rejecting…' : 'Reject document' }}
                  </button>
                </form>
              </div>
            }
          </section>
        </div>
      }
    </div>
  `,
  styles: [`
    .section-label {
      margin: 0;
      padding: 10px 12px;
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--fp-muted);
      background: var(--fp-surface-subtle);
      border-bottom: 1px solid var(--fp-line);
    }
    .preview-pane .section-label { border-radius: var(--fp-radius) var(--fp-radius) 0 0; }
    .meta-badges { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
    .doc-review {
      display: grid;
      grid-template-columns: minmax(320px, 1.05fr) minmax(360px, 0.95fr);
      gap: 20px;
      align-items: start;
    }
    .preview-pane {
      position: sticky;
      top: 16px;
      height: clamp(280px, 78vh, 860px);
      background: var(--fp-surface);
      border-radius: var(--fp-radius);
      border: 1px solid var(--fp-line);
      overflow: hidden;
      box-shadow: var(--fp-shadow);
    }
    .preview-pane ::ng-deep ngx-extended-pdf-viewer,
    .preview-pane ::ng-deep #viewerContainer {
      height: 100% !important;
    }
    .preview-image {
      width: 100%;
      height: 100%;
      object-fit: contain;
      background: #f8fafc;
    }
    .preview-fallback {
      margin: 0;
      padding: 24px;
      color: var(--fp-muted);
    }
    .review-panel {
      display: flex;
      flex-direction: column;
      gap: 16px;
      min-width: 0;
    }
    .doc-meta .file-name {
      font-family: var(--fp-display);
      font-size: 15px;
      font-weight: 600;
      color: var(--fp-ink);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      margin-bottom: 8px;
    }
    .meta-line {
      margin: 0 0 6px;
      font-size: 13px;
      line-height: 1.45;
      word-break: break-word;
    }
    .meta-line.muted, .muted { color: var(--fp-muted); }
    .sep { margin: 0 6px; color: var(--fp-muted); }
    .alert-banner {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 14px 16px;
      background: var(--fp-surface);
      border: 1px solid var(--fp-line);
      border-left: 4px solid var(--fp-orange);
      border-radius: 0 var(--fp-radius) var(--fp-radius) 0;
    }
    .alert-banner p {
      margin: 0;
      flex: 1 1 220px;
      color: #92400e;
      font-size: 14px;
      line-height: 1.45;
    }
    .facts-card, .form-card {
      background: var(--fp-surface);
      border: 1px solid var(--fp-line);
      border-radius: var(--fp-radius);
      box-shadow: var(--fp-shadow);
      padding: 16px 18px 14px;
    }
    .facts-card h2, .form-card h2 {
      margin: 0 0 12px;
      font-size: 15px;
    }
    .facts-list {
      margin: 0;
      display: flex;
      flex-direction: column;
    }
    .fact-row {
      display: grid;
      grid-template-columns: minmax(120px, 38%) 1fr;
      gap: 12px;
      align-items: start;
      padding: 10px 0;
      border-bottom: 1px solid var(--fp-line);
    }
    .fact-row:last-of-type { border-bottom: 0; }
    .fact-row dt {
      margin: 0;
      color: var(--fp-muted);
      font-size: 13px;
      font-weight: 500;
    }
    .fact-row dd {
      margin: 0;
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      justify-content: space-between;
      gap: 8px 12px;
      font-size: 14px;
      font-weight: 600;
      color: var(--fp-ink);
      min-width: 0;
    }
    .fact-row .confidence {
      font-size: 12px;
      font-weight: 500;
      color: var(--fp-muted);
    }
    .fact-row.low-confidence {
      background: var(--fp-orange-soft);
      margin: 0 -8px;
      padding-left: 8px;
      padding-right: 8px;
      border-radius: 8px;
    }
    .form-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 4px 12px;
      align-items: start;
    }
    .form-grid .span-2 { grid-column: 1 / -1; }
    .form-grid mat-form-field { width: 100%; }
    .align-field {
      margin-top: 4px;
      height: 56px;
    }
    .actions-row, .reject-row {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      align-items: center;
      margin-top: 8px;
    }
    .reject-row mat-form-field { flex: 1 1 240px; }
    .hint { margin: 8px 0 0; color: var(--fp-muted); font-size: 13px; }
    .hint.warn { color: #92400e; }
    @media (max-width: 980px) {
      .doc-review { grid-template-columns: 1fr; }
      .preview-pane { position: static; height: clamp(240px, 50vh, 520px); }
      .form-grid { grid-template-columns: 1fr; }
      .form-grid .span-2 { grid-column: auto; }
    }
  `]
})
export class DocumentReviewPage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);

  clientId = '';
  documentId = '';
  doc: any;
  previewUrl = '';
  isImage = false;
  isPdf = false;
  categories: any[] = [];
  filteredCategories: any[] = [];
  expenses: any[] = [];
  incomes: any[] = [];
  busy = false;
  loading = false;
  loadError = '';

  draftForm = this.fb.nonNullable.group({
    transactionType: ['EXPENSE', Validators.required],
    transactionDate: [null as Date | null, Validators.required],
    categoryId: ['', Validators.required],
    amount: [0 as number | null, amountValidators],
    taxAmount: [0 as number | null, [Validators.min(0)]],
    currencyCode: ['LKR', currencyCodeValidators],
    partyName: ['', [Validators.required, Validators.maxLength(200)]],
    description: [''],
    referenceNo: [''],
    paymentMethod: ['BANK_TRANSFER']
  });

  linkForm = this.fb.nonNullable.group({
    expenseId: [''],
    incomeId: ['']
  });

  rejectForm = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.minLength(3)]]
  });

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
    this.loading = true;
    this.loadError = '';
    this.api.get<any>(`/api/v1/clients/${this.clientId}/documents/${this.documentId}`).pipe(
      finalize(() => this.loading = false)
    ).subscribe({
      next: (doc) => {
        this.doc = doc;
        this.applySuggestion(doc);
        this.loadPreview(doc);
        this.api.get<any[]>(`/api/v1/categories`).subscribe((cats) => {
          this.categories = cats;
          this.filterCategories();
        });
        this.api.get<any>(`/api/v1/clients/${this.clientId}/expenses`, { size: 50 }).subscribe({
          next: (page) => this.expenses = (page.content ?? []).filter((row: any) => row.status !== 'VOID'),
          error: () => this.expenses = []
        });
        this.api.get<any>(`/api/v1/clients/${this.clientId}/income`, { size: 50 }).subscribe({
          next: (page) => this.incomes = (page.content ?? []).filter((row: any) => row.status !== 'VOID'),
          error: () => this.incomes = []
        });
      },
      error: (err: HttpErrorResponse) => {
        this.doc = undefined;
        this.loadError = err.error?.detail || 'This document could not be loaded.';
      }
    });
  }

  onTypeChange(): void {
    this.filterCategories();
    const type = this.draftForm.controls.transactionType.value;
    const payment = this.draftForm.controls.paymentMethod;
    if (type === 'INCOME') {
      payment.setValidators([Validators.required]);
    } else {
      payment.clearValidators();
    }
    payment.updateValueAndValidity();
    const stillValid = this.filteredCategories.some((cat) => cat.id === this.draftForm.controls.categoryId.value);
    if (!stillValid) {
      this.draftForm.controls.categoryId.setValue('');
    }
  }

  accept(): void {
    if (!this.ensureDraftValid() || this.busy) {
      return;
    }
    this.busy = true;
    this.api.post(`/api/v1/clients/${this.clientId}/documents/${this.documentId}/review/accept`, this.draftPayload()).subscribe({
      next: () => {
        this.toast.success('Draft created from suggestion. It is not approved.');
        this.reload();
        this.busy = false;
      },
      error: (err) => {
        this.toast.error(err.error?.detail || 'Unable to accept suggestion');
        this.busy = false;
      }
    });
  }

  rejectSuggestion(): void {
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.api.post(`/api/v1/clients/${this.clientId}/documents/${this.documentId}/review/reject-suggestion`, {
      note: 'Suggestion rejected'
    }).subscribe({
      next: () => {
        this.toast.success('AI suggestion rejected. The document remains available for a manual draft.');
        this.reload();
        this.busy = false;
      },
      error: (err) => {
        this.toast.error(err.error?.detail || 'Unable to reject suggestion');
        this.busy = false;
      }
    });
  }

  createDraft(): void {
    if (!this.ensureDraftValid() || this.busy) {
      return;
    }
    this.busy = true;
    this.api.post(`/api/v1/clients/${this.clientId}/documents/${this.documentId}/transactions`, this.draftPayload()).subscribe({
      next: () => {
        this.toast.success('Manual draft created. Approval is a separate step.');
        this.reload();
        this.busy = false;
      },
      error: (err) => {
        this.toast.error(err.error?.detail || 'Unable to create transaction');
        this.busy = false;
      }
    });
  }

  retry(): void {
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.api.post(`/api/v1/clients/${this.clientId}/documents/${this.documentId}/retry-processing`).subscribe({
      next: () => {
        this.toast.success('Processing queued.');
        this.reload();
        this.busy = false;
      },
      error: (err) => {
        this.toast.error(err.error?.detail || 'Unable to retry');
        this.busy = false;
      }
    });
  }

  linkExpense(): void {
    const expenseId = this.linkForm.controls.expenseId.value;
    if (!expenseId) {
      this.toast.error('Select an expense to link.');
      return;
    }
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.api.post(`/api/v1/clients/${this.clientId}/expenses/${expenseId}/documents`, { documentId: this.documentId }).subscribe({
      next: () => {
        this.toast.success('Document linked to expense.');
        this.reload();
        this.busy = false;
      },
      error: (err) => {
        this.toast.error(err.error?.detail || 'Unable to link expense');
        this.busy = false;
      }
    });
  }

  linkIncome(): void {
    const incomeId = this.linkForm.controls.incomeId.value;
    if (!incomeId) {
      this.toast.error('Select an income to link.');
      return;
    }
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.api.post(`/api/v1/clients/${this.clientId}/income/${incomeId}/documents`, { documentId: this.documentId }).subscribe({
      next: () => {
        this.toast.success('Document linked to income.');
        this.reload();
        this.busy = false;
      },
      error: (err) => {
        this.toast.error(err.error?.detail || 'Unable to link income');
        this.busy = false;
      }
    });
  }

  reject(): void {
    this.rejectForm.markAllAsTouched();
    if (this.rejectForm.invalid || this.busy) {
      if (this.rejectForm.invalid) {
        this.toast.error('A rejection reason is required.');
      }
      return;
    }
    this.busy = true;
    this.api.post(`/api/v1/clients/${this.clientId}/documents/${this.documentId}/reject`, {
      reason: this.rejectForm.controls.reason.value.trim()
    }).subscribe({
      next: () => {
        this.toast.success('Document rejected.');
        this.reload();
        this.busy = false;
      },
      error: (err) => {
        this.toast.error(err.error?.detail || 'Unable to reject document');
        this.busy = false;
      }
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

  displayValue(value: unknown): string {
    if (value === null || value === undefined || value === '') {
      return '—';
    }
    return String(value);
  }

  amountDisplay(): string {
    const amount = this.doc?.suggestedAmount;
    if (amount === null || amount === undefined || amount === '') {
      return '—';
    }
    const currency = this.doc?.suggestedCurrency ? ` ${this.doc.suggestedCurrency}` : '';
    return `${amount}${currency}`;
  }

  draftHeading(): string {
    if (this.doc?.status === 'FAILED' || this.doc?.failureCode) {
      return 'Enter transaction manually';
    }
    return 'Accounting suggestion';
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

  private ensureDraftValid(): boolean {
    this.draftForm.markAllAsTouched();
    if (this.draftForm.invalid) {
      this.toast.error('Fix the highlighted fields before saving.');
      return false;
    }
    return true;
  }

  private draftPayload(): Record<string, unknown> {
    const raw = this.draftForm.getRawValue();
    return {
      transactionType: raw.transactionType,
      transactionDate: formatIsoDate(raw.transactionDate),
      categoryId: raw.categoryId,
      amount: Number(raw.amount),
      taxAmount: Number(raw.taxAmount ?? 0),
      currencyCode: raw.currencyCode.toUpperCase(),
      partyName: raw.partyName.trim(),
      description: raw.description?.trim() || '',
      referenceNo: raw.referenceNo?.trim() || '',
      paymentMethod: raw.paymentMethod || 'BANK_TRANSFER'
    };
  }

  private applySuggestion(doc: any): void {
    const type = doc.suggestedType && doc.suggestedType !== 'UNKNOWN' ? doc.suggestedType : 'EXPENSE';
    this.draftForm.reset({
      transactionType: type,
      transactionDate: parseIsoDate(doc.suggestedDate),
      categoryId: doc.suggestedCategoryId || '',
      amount: doc.suggestedAmount != null && doc.suggestedAmount !== '' ? Number(doc.suggestedAmount) : null,
      taxAmount: doc.suggestedTaxAmount != null && doc.suggestedTaxAmount !== '' ? Number(doc.suggestedTaxAmount) : 0,
      currencyCode: doc.suggestedCurrency || 'LKR',
      partyName: doc.suggestedVendorOrCustomer || '',
      description: doc.suggestedDescription || '',
      referenceNo: doc.suggestedInvoiceNo || '',
      paymentMethod: doc.suggestedPaymentMethod || 'BANK_TRANSFER'
    });
    this.onTypeChange();
    this.draftForm.markAsPristine();
    this.draftForm.markAsUntouched();
  }

  private filterCategories(): void {
    const type = this.draftForm.controls.transactionType.value;
    this.filteredCategories = this.categories.filter((cat) => !cat.categoryType || cat.categoryType === type);
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
    });
  }
}
