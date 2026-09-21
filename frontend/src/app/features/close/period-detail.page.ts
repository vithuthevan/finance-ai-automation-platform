import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ReportContextService, money } from '../reports/report-context.service';
import { saveBlob } from '../reports/report-download';
import { AccountingPeriod, CloseFinding, DocumentRequestRow, PeriodReadiness } from './close.models';
import { formatIsoDate, parseIsoDate } from '../../shared/date.util';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';

@Component({
  standalone: true,
  imports: [
    ReactiveFormsModule, RouterLink, DatePipe, MatCardModule, MatButtonModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatDatepickerModule, PageHeaderComponent, StatusBadgeComponent
  ],
  template: `
    <div class="page">
      @if (period) {
        <app-page-header
          [title]="period.clientName"
          [subtitle]="monthTitle + ' · ' + period.startDate + ' – ' + period.endDate"
          backLink="/app/close"
          backLabel="← Month-end close" />
        <div class="toolbar-row">
          <app-status-badge [status]="period.status" [label]="statusLabel(period.status)" />
          <span class="value fp-num">Readiness {{ period.readinessPercent }}%</span>
          @if (period.status === 'CLOSED') {
            <span class="hint">Closed period · Finalized bookkeeping period (not an audited statement)</span>
          }
        </div>
        @if (period.status === 'CLOSED') {
          <p>Closed by {{ period.closedByName || '—' }} @if (period.closedAt) { at {{ period.closedAt | date:'medium' }} }</p>
          @if (period.closeNote) { <p class="hint">{{ period.closeNote }}</p> }
        }
        @if (period.reopenedAt) {
          <p class="hint">Last reopened by {{ period.reopenedByName }}: {{ period.reopenReason }}</p>
        }
      }

      @if (message) { <p class="hint">{{ message }}</p> }

      @if (readiness) {
        <section class="card-block">
          <h2>Readiness checks</h2>
          <p class="hint">The period cannot close until blockers are resolved.</p>
          <ul class="readiness-list">
            @for (item of readiness.checklist; track item.code) {
              @if (item.severity !== 'DISABLED') {
                <li class="readiness-item" [class]="item.passed ? 'readiness-item--pass' : 'readiness-item--fail'">
                  <span aria-hidden="true">{{ item.passed ? '✓' : '✗' }}</span>
                  <span>{{ item.label }}@if (!item.passed && item.count) { · {{ item.count }} }</span>
                </li>
              }
            }
          </ul>
          @if (readiness.blockers.length === 0) {
            <p class="hint">No blockers. You may close after final review.</p>
          } @else {
            <h3>Blocking issues</h3>
            <ul class="readiness-list">
              @for (row of readiness.blockers; track row.code) {
                <li class="readiness-item readiness-item--fail">
                  @if (row.responsibilityLabel) {
                    <span class="hint">{{ row.responsibilityLabel }} · </span>
                  }
                  <a [routerLink]="blockerLink(row)" [queryParams]="blockerQuery(row)">{{ row.message }}</a>
                </li>
              }
            </ul>
          }
        </section>

        <section class="card-block">
          <h2>Warnings</h2>
          @if (readiness.warnings.length === 0) {
            <p class="hint">No warnings.</p>
          } @else {
            <ul class="readiness-list">
              @for (row of readiness.warnings; track row.code) {
                <li class="readiness-item readiness-item--warn">{{ row.message }}</li>
              }
            </ul>
          }
        </section>

        <section class="grid-2">
          <mat-card class="metric-card">
            <div class="label">Approved income</div>
            <div class="value">{{ format(readiness.ledger.approvedIncomeAmount) }}</div>
            <p class="hint">{{ readiness.ledger.approvedIncome }} approved · {{ readiness.ledger.draftIncome }} draft · {{ readiness.ledger.voidIncome }} void</p>
          </mat-card>
          <mat-card class="metric-card">
            <div class="label">Approved expenses</div>
            <div class="value">{{ format(readiness.ledger.approvedExpenseAmount) }}</div>
            <p class="hint">{{ readiness.ledger.approvedExpenses }} approved · {{ readiness.ledger.draftExpenses }} draft · {{ readiness.ledger.voidExpenses }} void</p>
          </mat-card>
          <mat-card class="metric-card">
            <div class="label">Net (approved)</div>
            <div class="value">{{ format(readiness.ledger.netApproved) }}</div>
          </mat-card>
          <mat-card class="metric-card">
            <div class="label">Documents</div>
            <div class="value">{{ readiness.documents.total }}</div>
            <p class="hint">{{ readiness.documents.linked }} linked · {{ readiness.documents.unlinked }} unlinked · {{ readiness.documents.needsReview }} need review · {{ readiness.documents.rejected }} rejected · {{ readiness.documents.failed }} failed</p>
          </mat-card>
        </section>
      }

      <div class="toolbar-row">
        <button mat-stroked-button (click)="viewPnL()">View Profit &amp; Loss</button>
        <button mat-stroked-button (click)="exportFile('xlsx')" [disabled]="exporting">Download Excel</button>
        <button mat-stroked-button (click)="exportFile('csv')" [disabled]="exporting">Download CSV</button>
        @if (canOperate && period && period.status !== 'CLOSED' && period.status !== 'IN_REVIEW') {
          <button mat-stroked-button (click)="startReview()">Start review</button>
        }
        @if (auth.hasRole('ADMIN') && period?.status === 'CLOSED') {
          <button mat-stroked-button color="warn" (click)="showReopen = true">Reopen period</button>
        }
      </div>

      @if (canOperate && period && period.status !== 'CLOSED') {
        <section class="card-block">
          <h2>Close period</h2>
          @if (readiness && !readiness.ready) {
            <p class="hint">Close is disabled until blockers are resolved. The server re-checks readiness independently.</p>
          }
          <form [formGroup]="closeForm" (ngSubmit)="closePeriod()" novalidate>
            <div class="form-grid">
              <mat-form-field class="span-2">
                <mat-label>Close note (optional)</mat-label>
                <input matInput formControlName="closeNote">
              </mat-form-field>
            </div>
            <div class="form-actions">
              <button mat-flat-button color="primary" type="submit" [disabled]="!!readiness && !readiness.ready">
                Close period
              </button>
            </div>
          </form>
        </section>
      }

      @if (showReopen) {
        <section class="card-block">
          <h2>Reopen period</h2>
          <p>Reopening allows financial changes to a previously closed period and will be recorded in the audit trail.</p>
          <form [formGroup]="reopenForm" (ngSubmit)="reopen()" novalidate>
            <div class="form-grid">
              <mat-form-field class="span-2">
                <mat-label>Reason (required)</mat-label>
                <textarea matInput rows="3" formControlName="reason"></textarea>
                @if (reopenForm.controls.reason.touched && reopenForm.controls.reason.hasError('required')) {
                  <mat-error>A reopen reason is required</mat-error>
                }
              </mat-form-field>
            </div>
            <div class="form-actions">
              <button mat-flat-button color="warn" type="submit" [disabled]="reopenForm.invalid">Confirm reopen</button>
              <button mat-button type="button" (click)="cancelReopen()">Cancel</button>
            </div>
          </form>
        </section>
      }

      @if (canOperate && period?.status !== 'CLOSED') {
        <section class="card-block" id="requests">
          <h2>Request missing document</h2>
          <form [formGroup]="requestForm" (ngSubmit)="createRequest()" novalidate>
            <div class="form-grid">
              <mat-form-field class="span-2">
                <mat-label>What you need</mat-label>
                <input matInput formControlName="description">
                @if (requestForm.controls.description.touched && requestForm.controls.description.hasError('required')) {
                  <mat-error>Describe the document you need</mat-error>
                }
              </mat-form-field>
              <mat-form-field>
                <mat-label>Type</mat-label>
                <mat-select formControlName="documentType">
                  <mat-option value="RECEIPT">Receipt</mat-option>
                  <mat-option value="PURCHASE_INVOICE">Purchase invoice</mat-option>
                  <mat-option value="SALES_INVOICE">Sales invoice</mat-option>
                  <mat-option value="BANK_STATEMENT">Bank statement</mat-option>
                  <mat-option value="OTHER">Other</mat-option>
                </mat-select>
              </mat-form-field>
              <mat-form-field>
                <mat-label>Due</mat-label>
                <input matInput [matDatepicker]="duePicker" formControlName="dueDate">
                <mat-datepicker-toggle matIconSuffix [for]="duePicker"></mat-datepicker-toggle>
                <mat-datepicker #duePicker></mat-datepicker>
              </mat-form-field>
            </div>
            <div class="form-actions">
              <button mat-stroked-button type="submit" [disabled]="requestForm.invalid">Request</button>
            </div>
          </form>
        </section>
      }

      <section class="card-block">
        <h2>Document requests</h2>
        @for (req of requests; track req.id) {
          <p>
            {{ req.description }} · {{ req.documentType }} · {{ req.status }}
            @if (req.dueDate) { · due {{ req.dueDate }} }
            @if (canOperate && req.status === 'UPLOADED') {
              <button mat-button (click)="completeRequest(req)">Mark complete</button>
            }
            @if (canOperate && (req.status === 'OPEN' || req.status === 'UPLOADED')) {
              <button mat-button (click)="cancelRequest(req)">Cancel</button>
            }
          </p>
        }
        @if (requests.length === 0) { <p class="hint">No requests for this period.</p> }
      </section>
    </div>
  `
})
export class PeriodDetailPage implements OnInit {
  private readonly fb = inject(FormBuilder);

  clientId = '';
  periodId = '';
  period: AccountingPeriod | null = null;
  readiness: PeriodReadiness | null = null;
  requests: DocumentRequestRow[] = [];
  message = '';
  exporting = false;
  showReopen = false;

  closeForm = this.fb.nonNullable.group({
    closeNote: ['']
  });

  reopenForm = this.fb.nonNullable.group({
    reason: ['', Validators.required]
  });

  requestForm = this.fb.nonNullable.group({
    description: ['', Validators.required],
    documentType: ['RECEIPT' as const],
    dueDate: [null as Date | null]
  });

  constructor(
    private api: ApiService,
    private route: ActivatedRoute,
    private router: Router,
    readonly auth: AuthService,
    private ctx: ReportContextService
  ) {}

  get canOperate(): boolean {
    return this.auth.hasRole('ADMIN', 'ACCOUNTANT');
  }

  get monthTitle(): string {
    if (!this.period) {
      return '';
    }
    const names = ['January','February','March','April','May','June','July','August','September','October','November','December'];
    return `${names[this.period.month - 1] ?? ''} ${this.period.year}`;
  }

  ngOnInit(): void {
    this.clientId = this.route.snapshot.paramMap.get('clientId') ?? '';
    this.periodId = this.route.snapshot.paramMap.get('periodId') ?? '';
    this.reload();
  }

  format(value: unknown): string {
    return money(value);
  }

  statusLabel(status: string): string {
    if (status === 'IN_REVIEW') return 'IN REVIEW';
    if (status === 'CLOSED') return 'CLOSED';
    if (status === 'REOPENED') return 'REOPENED';
    return status;
  }

  reload(): void {
    this.message = '';
    this.api.get<AccountingPeriod>(`/api/v1/clients/${this.clientId}/periods/${this.periodId}`).subscribe({
      next: (period) => this.period = period,
      error: (err) => this.message = err.error?.detail || 'Period not found'
    });
    this.api.get<PeriodReadiness>(`/api/v1/clients/${this.clientId}/periods/${this.periodId}/readiness`).subscribe({
      next: (readiness) => this.readiness = readiness,
      error: () => this.readiness = null
    });
    this.api.list<DocumentRequestRow>(`/api/v1/clients/${this.clientId}/document-requests`, {
      periodId: this.periodId,
      size: 50
    }).subscribe((requests) => this.requests = requests);
  }

  blockerLink(row: CloseFinding): string {
    if (row.actionHint === 'EXPENSES_DRAFT' || row.actionHint === 'TRANSACTIONS_UNSUPPORTED') {
      return '/app/expenses';
    }
    if (row.actionHint === 'INCOME_DRAFT') {
      return '/app/income';
    }
    if (row.actionHint === 'DOCUMENT_REQUESTS') {
      return `/app/close/${this.clientId}/${this.periodId}`;
    }
    if (row.actionHint?.startsWith('BANKING')) {
      return '/app/banking';
    }
    return '/app/documents';
  }

  blockerQuery(row: CloseFinding): Record<string, string> {
    const dates: Record<string, string> = this.period
      ? { from: this.period.startDate, to: this.period.endDate, clientId: this.clientId }
      : { clientId: this.clientId };
    if (row.actionHint === 'EXPENSES_DRAFT') {
      return { ...dates, status: 'DRAFT' };
    }
    if (row.actionHint === 'INCOME_DRAFT') {
      return { ...dates, status: 'DRAFT' };
    }
    if (row.actionHint === 'DOCUMENTS_REVIEW') {
      return { ...dates, status: 'NEEDS_REVIEW' };
    }
    if (row.actionHint === 'DOCUMENTS_FAILED') {
      return { ...dates, status: 'FAILED' };
    }
    if (row.actionHint === 'DOCUMENTS_UNLINKED') {
      return { ...dates, linked: 'false' };
    }
    if (row.actionHint === 'TRANSACTIONS_UNSUPPORTED') {
      return { ...dates, status: 'APPROVED' };
    }
    return dates;
  }

  viewPnL(): void {
    if (!this.period) {
      return;
    }
    this.ctx.clientId.set(this.clientId);
    this.ctx.from.set(this.period.startDate);
    this.ctx.to.set(this.period.endDate);
    this.router.navigate(['/app/reports']);
  }

  exportFile(format: string): void {
    if (!this.period) {
      return;
    }
    this.exporting = true;
    this.api.downloadAttachment(`/api/v1/clients/${this.clientId}/reports/profit-and-loss/export`, {
      from: this.period.startDate,
      to: this.period.endDate,
      format
    }).subscribe({
      next: (file) => {
        saveBlob(file.blob, file.filename || `PnL.${format}`);
        this.exporting = false;
      },
      error: () => {
        this.message = 'Export failed';
        this.exporting = false;
      }
    });
  }

  startReview(): void {
    this.api.post(`/api/v1/clients/${this.clientId}/periods/${this.periodId}/review`).subscribe({
      next: () => this.reload(),
      error: (err) => this.message = err.error?.detail || 'Unable to start review'
    });
  }

  closePeriod(): void {
    const closeNote = this.closeForm.controls.closeNote.value.trim();
    this.api.post(`/api/v1/clients/${this.clientId}/periods/${this.periodId}/close`, { closeNote: closeNote || undefined }).subscribe({
      next: () => {
        this.closeForm.reset();
        this.reload();
      },
      error: (err) => {
        const blockers = err.error?.blockers as CloseFinding[] | undefined;
        this.message = blockers?.length
          ? 'Cannot close: ' + blockers.map((row) => row.message).join(' ')
          : (err.error?.detail || 'Cannot close this period');
        this.reload();
      }
    });
  }

  cancelReopen(): void {
    this.showReopen = false;
    this.reopenForm.reset();
  }

  reopen(): void {
    if (this.reopenForm.invalid) {
      this.reopenForm.markAllAsTouched();
      return;
    }
    const reason = this.reopenForm.controls.reason.value.trim();
    if (!reason) {
      this.reopenForm.controls.reason.setErrors({ required: true });
      this.reopenForm.controls.reason.markAsTouched();
      return;
    }
    this.api.post(`/api/v1/clients/${this.clientId}/periods/${this.periodId}/reopen`, { reason }).subscribe({
      next: () => {
        this.showReopen = false;
        this.reopenForm.reset();
        this.reload();
      },
      error: (err) => this.message = err.error?.detail || 'Unable to reopen'
    });
  }

  createRequest(): void {
    if (this.requestForm.invalid) {
      this.requestForm.markAllAsTouched();
      return;
    }
    const { description, documentType, dueDate } = this.requestForm.getRawValue();
    const trimmed = description.trim();
    if (!trimmed) {
      this.requestForm.controls.description.setErrors({ required: true });
      this.requestForm.controls.description.markAsTouched();
      return;
    }
    this.api.post(`/api/v1/clients/${this.clientId}/document-requests`, {
      description: trimmed,
      documentType,
      dueDate: dueDate ? formatIsoDate(dueDate) : undefined,
      periodId: this.periodId
    }).subscribe({
      next: () => {
        this.requestForm.reset({ documentType: 'RECEIPT', dueDate: null });
        this.reload();
      },
      error: (err) => this.message = err.error?.detail || 'Unable to create request'
    });
  }

  completeRequest(req: DocumentRequestRow): void {
    this.api.post(`/api/v1/clients/${this.clientId}/document-requests/${req.id}/complete`).subscribe({
      next: () => this.reload(),
      error: (err) => this.message = err.error?.detail || 'Unable to complete request'
    });
  }

  cancelRequest(req: DocumentRequestRow): void {
    this.api.post(`/api/v1/clients/${this.clientId}/document-requests/${req.id}/cancel`).subscribe({
      next: () => this.reload(),
      error: (err) => this.message = err.error?.detail || 'Unable to cancel request'
    });
  }
}
