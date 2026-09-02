import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ReportContextService, money } from '../reports/report-context.service';
import { saveBlob } from '../reports/report-download';
import { AccountingPeriod, CloseFinding, DocumentRequestRow, PeriodReadiness } from './close.models';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, MatCardModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <div class="page">
      <p><a routerLink="/app/close">← Month-end close</a></p>
      @if (period) {
        <h1>{{ period.clientName }}</h1>
        <p class="hint">{{ monthTitle }} · {{ period.startDate }} – {{ period.endDate }}</p>
        <div class="toolbar-row">
          <span class="status-pill" [class]="'status-' + period.status">{{ statusLabel(period.status) }}</span>
          <span class="value">Readiness {{ period.readinessPercent }}%</span>
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
          <h2>Blockers</h2>
          <p class="hint">The period cannot close until these are resolved.</p>
          @for (item of readiness.checklist; track item.code) {
            @if (item.severity !== 'DISABLED') {
              <p>
                <span>{{ item.passed ? '✓' : '✗' }}</span>
                {{ item.label }}
                @if (!item.passed && item.count) { · {{ item.count }} }
              </p>
            }
          }
          @if (readiness.blockers.length === 0) {
            <p>No blockers. This period can be closed if you have reviewed the books.</p>
          } @else {
            @for (row of readiness.blockers; track row.code) {
              <p>
                <a [routerLink]="blockerLink(row)" [queryParams]="blockerQuery(row)">{{ row.message }}</a>
              </p>
            }
          }
        </section>

        <section class="card-block">
          <h2>Warnings</h2>
          @if (readiness.warnings.length === 0) {
            <p class="hint">No warnings.</p>
          } @else {
            @for (row of readiness.warnings; track row.code) {
              <p>⚠ {{ row.message }}</p>
            }
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
        @if (canOperate && period && period.status !== 'CLOSED') {
          <mat-form-field><mat-label>Close note (optional)</mat-label><input matInput [(ngModel)]="closeNote"></mat-form-field>
          <button mat-flat-button color="primary" (click)="closePeriod()" [disabled]="!!readiness && !readiness.ready">
            Close period
          </button>
        }
        @if (auth.hasRole('ADMIN') && period?.status === 'CLOSED') {
          <button mat-stroked-button color="warn" (click)="showReopen = true">Reopen period</button>
        }
      </div>
      @if (readiness && !readiness.ready && period?.status !== 'CLOSED') {
        <p class="hint">Close is disabled until blockers are resolved. The server re-checks readiness independently.</p>
      }

      @if (showReopen) {
        <section class="card-block">
          <h2>Reopen period</h2>
          <p>Reopening allows financial changes to a previously closed period and will be recorded in the audit trail.</p>
          <mat-form-field class="full-width"><mat-label>Reason (required)</mat-label>
            <textarea matInput rows="3" [(ngModel)]="reopenReason"></textarea>
          </mat-form-field>
          <button mat-flat-button color="warn" (click)="reopen()">Confirm reopen</button>
          <button mat-button (click)="showReopen = false">Cancel</button>
        </section>
      }

      @if (canOperate && period?.status !== 'CLOSED') {
        <section class="card-block" id="requests">
          <h2>Request missing document</h2>
          <div class="toolbar-row">
            <mat-form-field><mat-label>What you need</mat-label><input matInput [(ngModel)]="requestDescription"></mat-form-field>
            <mat-form-field><mat-label>Type</mat-label>
              <mat-select [(ngModel)]="requestType">
                <mat-option value="RECEIPT">Receipt</mat-option>
                <mat-option value="PURCHASE_INVOICE">Purchase invoice</mat-option>
                <mat-option value="SALES_INVOICE">Sales invoice</mat-option>
                <mat-option value="BANK_STATEMENT">Bank statement</mat-option>
                <mat-option value="OTHER">Other</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field><mat-label>Due</mat-label><input matInput type="date" [(ngModel)]="requestDue"></mat-form-field>
            <button mat-stroked-button (click)="createRequest()">Request</button>
          </div>
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
  clientId = '';
  periodId = '';
  period: AccountingPeriod | null = null;
  readiness: PeriodReadiness | null = null;
  requests: DocumentRequestRow[] = [];
  message = '';
  exporting = false;
  showReopen = false;
  reopenReason = '';
  requestDescription = '';
  requestType = 'RECEIPT';
  requestDue = '';
  closeNote = '';

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
    this.api.post(`/api/v1/clients/${this.clientId}/periods/${this.periodId}/close`, { closeNote: this.closeNote || undefined }).subscribe({
      next: () => this.reload(),
      error: (err) => {
        const blockers = err.error?.blockers as CloseFinding[] | undefined;
        this.message = blockers?.length
          ? 'Cannot close: ' + blockers.map((row) => row.message).join(' ')
          : (err.error?.detail || 'Cannot close this period');
        this.reload();
      }
    });
  }

  reopen(): void {
    if (!this.reopenReason.trim()) {
      this.message = 'A reopen reason is required.';
      return;
    }
    this.api.post(`/api/v1/clients/${this.clientId}/periods/${this.periodId}/reopen`, { reason: this.reopenReason.trim() }).subscribe({
      next: () => {
        this.showReopen = false;
        this.reopenReason = '';
        this.reload();
      },
      error: (err) => this.message = err.error?.detail || 'Unable to reopen'
    });
  }

  createRequest(): void {
    if (!this.requestDescription.trim()) {
      return;
    }
    this.api.post(`/api/v1/clients/${this.clientId}/document-requests`, {
      description: this.requestDescription.trim(),
      documentType: this.requestType,
      dueDate: this.requestDue || undefined,
      periodId: this.periodId
    }).subscribe({
      next: () => {
        this.requestDescription = '';
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
