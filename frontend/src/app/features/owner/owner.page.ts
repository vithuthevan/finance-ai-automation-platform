import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { monthStart, today } from '../reports/report-context.service';
import { PlSummary } from '../reports/report.models';
import { DocumentRequestRow } from '../close/close.models';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';
import { FileDropzoneComponent } from '../../shared/file-dropzone.component';
import { MoneyDisplayComponent } from '../../shared/ui/money-display.component';

@Component({
  standalone: true,
  imports: [
    FormsModule, RouterLink, MatCardModule, MatButtonModule, MatFormFieldModule, MatSelectModule,
    PageHeaderComponent, StatusBadgeComponent, EmptyStateComponent, LoadingStateComponent, ErrorStateComponent,
    FileDropzoneComponent, MoneyDisplayComponent
  ],
  template: `
    <div class="page owner-home">
      <app-page-header
        [title]="auth.isUploadOnly() ? 'Documents your accountant needs' : 'Your business hub'"
        [subtitle]="auth.isUploadOnly()
          ? 'Upload what your accountant requested — no bookkeeping menus required.'
          : 'Send documents and see a simple summary of approved activity.'" />

      @if (clients.length > 1) {
        <div class="filter-bar">
          <mat-form-field appearance="outline">
            <mat-label>Business</mat-label>
            <mat-select [(ngModel)]="clientId" (selectionChange)="reload()">
              @for (c of clients; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
            </mat-select>
          </mat-form-field>
        </div>
      }

      @if (loadError) {
        <app-error-state title="Could not load requests" [message]="loadError" (retry)="reload()" />
      } @else if (loading) {
        <app-loading-state message="Loading your requests…" />
      } @else {
        <section class="card-block">
          <div class="toolbar-row">
            <div>
              <h2>Document requests</h2>
              <p class="hint">Open → upload your file → your accountant completes the review.</p>
            </div>
            <a mat-stroked-button routerLink="/app/documents">View my documents</a>
          </div>

          @for (req of openRequests; track req.id) {
            <mat-card class="metric-card request-card" [class.overdue]="isOverdue(req)">
              <div class="request-head">
                <div>
                  <div class="request-title">{{ displayTitle(req) }}</div>
                  <div class="request-meta">
                    {{ req.documentType }}
                    @if (req.dueDate) { · Due {{ req.dueDate }} }
                  </div>
                </div>
                <app-status-badge [status]="req.status" />
              </div>
              @if (req.description) { <p class="request-desc">{{ req.description }}</p> }

              <div class="owner-request-steps" aria-label="Request progress">
                <span class="step" [class.is-done]="req.status !== 'OPEN'" [class.is-active]="req.status === 'OPEN'">1 · Requested</span>
                <span aria-hidden="true">→</span>
                <span class="step" [class.is-done]="req.status === 'UPLOADED' || req.status === 'COMPLETED'" [class.is-active]="req.status === 'UPLOADED'">2 · Uploaded</span>
                <span aria-hidden="true">→</span>
                <span class="step" [class.is-done]="req.status === 'COMPLETED'" [class.is-active]="req.status === 'COMPLETED'">3 · Completed</span>
              </div>

              @if (req.status === 'OPEN' || req.status === 'UPLOADED') {
                <div class="upload-row">
                  <app-file-dropzone
                    accept=".pdf,.jpg,.jpeg,.png,.webp"
                    [fileName]="requestFiles[req.id]?.name || ''"
                    hint="PDF or image"
                    (fileSelected)="onRequestFileSelected($event, req.id)" />
                  <button mat-flat-button color="primary" type="button" (click)="uploadRequest(req)"
                    [disabled]="!requestFiles[req.id] || uploadingId === req.id">
                    {{ uploadingId === req.id ? 'Uploading…' : (req.status === 'OPEN' ? 'Upload file' : 'Replace file') }}
                  </button>
                </div>
              }
              @if (req.status === 'UPLOADED') {
                <p class="hint">File received — your accountant will review and mark complete.</p>
              }
            </mat-card>
          }

          @if (openRequests.length === 0) {
            <app-empty-state
              title="No open requests"
              description="When your accountant needs a document, it will appear here with a due date."
              icon="assignment" />
          }
        </section>

        @if (completedRequests.length) {
          <section class="card-block">
            <h2>Completed requests</h2>
            @for (req of completedRequests; track req.id) {
              <mat-card class="metric-card muted">
                <div class="request-head">
                  <div class="request-title">{{ displayTitle(req) }}</div>
                  <app-status-badge [status]="req.status" />
                </div>
              </mat-card>
            }
          </section>
        }

        @if (!auth.isUploadOnly()) {
          <section class="card-block">
            <h2>Upload other documents</h2>
            <p class="hint">Receipts, invoices, or bank statements not tied to a specific request.</p>
            <app-file-dropzone
              accept=".pdf,.jpg,.jpeg,.png,.webp,.csv"
              [fileName]="file?.name || ''"
              (fileSelected)="onFile($event)" />
            <div class="form-actions">
              <button mat-flat-button color="primary" type="button" (click)="upload('RECEIPT')" [disabled]="!file || generalUploading">Upload receipt</button>
              <button mat-stroked-button type="button" (click)="upload('PURCHASE_INVOICE')" [disabled]="!file || generalUploading">Upload invoice</button>
              <button mat-stroked-button type="button" (click)="upload('BANK_STATEMENT')" [disabled]="!file || generalUploading">Upload bank statement</button>
            </div>
          </section>

          <section class="card-block">
            <h2>Approved totals this month</h2>
            @if (summary && !summary.hasApprovedData) {
              <app-empty-state title="No approved activity yet" description="Totals appear after your accountant approves transactions." icon="insights" />
            } @else if (summary) {
              <div class="grid-2">
                <mat-card class="metric-card">
                  <div class="label">Income</div>
                  <app-money-display [value]="summary.totalIncome" [currency]="summary.currencyCode" [emphasis]="true" />
                </mat-card>
                <mat-card class="metric-card">
                  <div class="label">Expenses</div>
                  <app-money-display [value]="summary.totalExpenses" [currency]="summary.currencyCode" [emphasis]="true" />
                </mat-card>
                <mat-card class="metric-card">
                  <div class="label">{{ summary.resultType }}</div>
                  <app-money-display [value]="summary.netResult" [currency]="summary.currencyCode" [emphasis]="true" />
                </mat-card>
              </div>
              <a mat-button routerLink="/app/reports">Open financial summary</a>
            }
          </section>
        }
      }
    </div>
  `,
  styles: [`
    .request-card { margin-bottom: 12px; }
    .request-card.overdue { border-left: 4px solid var(--fp-danger); }
    .request-head { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; }
    .request-title { font-weight: 600; color: var(--fp-ink); }
    .request-meta, .request-desc { font-size: 13px; color: var(--fp-muted); margin-top: 4px; }
    .request-desc { margin: 8px 0 0; }
    .upload-row { margin-top: 12px; display: flex; flex-direction: column; gap: 10px; }
    .muted { opacity: .85; margin-bottom: 8px; }
    @media (max-width: 640px) {
      .request-head { flex-direction: column; }
    }
  `]
})
export class OwnerPage implements OnInit {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);

  clients: any[] = [];
  requests: DocumentRequestRow[] = [];
  summary: PlSummary | null = null;
  clientId = '';
  file: File | null = null;
  requestFiles: Record<string, File> = {};
  loading = false;
  loadError = '';
  uploadingId: string | null = null;
  generalUploading = false;

  ngOnInit(): void {
    this.api.list<any>('/api/v1/clients').subscribe((clients) => {
      this.clients = clients;
      this.clientId = clients[0]?.id ?? '';
      this.reload();
    });
  }

  reload(): void {
    if (!this.clientId) {
      return;
    }
    this.loading = true;
    this.loadError = '';
    this.api.list<DocumentRequestRow>(`/api/v1/clients/${this.clientId}/document-requests`, { size: 50 }).pipe(
      finalize(() => this.loading = false)
    ).subscribe({
      next: (requests) => this.requests = requests,
      error: () => {
        this.requests = [];
        this.loadError = 'Unable to load document requests.';
      }
    });
    if (this.auth.isUploadOnly()) {
      this.summary = null;
      return;
    }
    this.api.get<PlSummary>(`/api/v1/clients/${this.clientId}/reports/profit-and-loss`, {
      from: monthStart(),
      to: today()
    }).subscribe({
      next: (summary) => this.summary = summary,
      error: () => this.summary = null
    });
  }

  onFile(file: File): void {
    this.file = file;
  }

  onRequestFileSelected(file: File, requestId: string): void {
    this.requestFiles[requestId] = file;
  }

  upload(type: string): void {
    if (!this.file || !this.clientId || this.generalUploading) {
      return;
    }
    this.generalUploading = true;
    this.api.upload(`/api/v1/clients/${this.clientId}/documents`, this.file, { documentType: type }).pipe(
      finalize(() => this.generalUploading = false)
    ).subscribe(() => {
      this.file = null;
      this.reload();
    });
  }

  uploadRequest(req: DocumentRequestRow): void {
    const file = this.requestFiles[req.id];
    if (!file || !this.clientId || this.uploadingId) {
      return;
    }
    this.uploadingId = req.id;
    this.api.upload(`/api/v1/clients/${this.clientId}/document-requests/${req.id}/upload`, file).pipe(
      finalize(() => this.uploadingId = null)
    ).subscribe({
      next: () => {
        delete this.requestFiles[req.id];
        this.reload();
      }
    });
  }

  get openRequests(): DocumentRequestRow[] {
    return this.requests.filter((req) => req.status === 'OPEN' || req.status === 'UPLOADED');
  }

  get completedRequests(): DocumentRequestRow[] {
    return this.requests.filter((req) => req.status === 'COMPLETED' || req.status === 'CANCELLED');
  }

  displayTitle(req: DocumentRequestRow): string {
    return req.title?.trim() || req.description;
  }

  isOverdue(req: DocumentRequestRow): boolean {
    if (!req.dueDate || req.status === 'COMPLETED' || req.status === 'CANCELLED') {
      return false;
    }
    return req.dueDate < new Date().toISOString().slice(0, 10);
  }
}
