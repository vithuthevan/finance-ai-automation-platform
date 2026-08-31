import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { money, monthStart, today } from '../reports/report-context.service';
import { PlSummary } from '../reports/report.models';
import { DocumentRequestRow } from '../close/close.models';

@Component({
  standalone: true,
  imports: [FormsModule, MatCardModule, MatButtonModule, MatFormFieldModule, MatSelectModule],
  template: `
    <div class="page">
      <h1>{{ auth.isUploadOnly() ? 'Documents your accountant needs' : 'Send documents to your accountant' }}</h1>
      <div class="toolbar-row">
        <mat-form-field><mat-label>Business</mat-label>
          <mat-select [(ngModel)]="clientId" (selectionChange)="reload()">
            @for (c of clients; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
          </mat-select>
        </mat-form-field>
      </div>

      <h2>Your accountant needs</h2>
      @for (req of requests; track req.id) {
        <mat-card class="metric-card">
          <div class="label">{{ req.documentType }} · {{ req.status }}@if (req.dueDate) { · due {{ req.dueDate }} }</div>
          <div>{{ req.description }}</div>
          @if (req.status === 'OPEN' || req.status === 'UPLOADED') {
            <div class="toolbar-row">
              <input type="file" (change)="onRequestFile($event, req.id)">
              <button mat-flat-button color="primary" (click)="uploadRequest(req)" [disabled]="!requestFiles[req.id]">Upload</button>
            </div>
          }
          @if (req.status === 'UPLOADED') {
            <p class="hint">Received — your accountant still needs to accept this file.</p>
          }
        </mat-card>
      }
      @if (requests.length === 0) {
        <p class="hint">No document requests right now.</p>
      }

      @if (!auth.isUploadOnly()) {
        <h2>Upload other documents</h2>
        <div class="toolbar-row">
          <input type="file" (change)="onFile($event)">
          <button mat-flat-button color="primary" (click)="upload('RECEIPT')">Upload receipt</button>
          <button mat-stroked-button (click)="upload('PURCHASE_INVOICE')">Upload invoice</button>
          <button mat-stroked-button (click)="upload('BANK_STATEMENT')">Upload bank statement</button>
        </div>
        <h2>Approved totals this month</h2>
        @if (summary && !summary.hasApprovedData) {
          <p class="empty-report">No approved activity yet this month.</p>
        } @else if (summary) {
          <div class="grid-2">
            <mat-card class="metric-card"><div class="label">Income</div><div class="value">{{ format(summary.totalIncome) }} {{ summary.currencyCode }}</div></mat-card>
            <mat-card class="metric-card"><div class="label">Expenses</div><div class="value">{{ format(summary.totalExpenses) }} {{ summary.currencyCode }}</div></mat-card>
            <mat-card class="metric-card"><div class="label">{{ summary.resultType }}</div><div class="value">{{ format(summary.netResult) }} {{ summary.currencyCode }}</div></mat-card>
          </div>
        }
      }
    </div>
  `
})
export class OwnerPage implements OnInit {
  clients: any[] = [];
  requests: DocumentRequestRow[] = [];
  summary: PlSummary | null = null;
  clientId = '';
  file: File | null = null;
  requestFiles: Record<string, File> = {};

  constructor(private api: ApiService, readonly auth: AuthService) {}

  ngOnInit(): void {
    this.api.list<any>('/api/v1/clients').subscribe((clients) => {
      this.clients = clients;
      this.clientId = clients[0]?.id ?? '';
      this.reload();
    });
  }

  reload(): void {
    if (!this.clientId) return;
    this.api.list<DocumentRequestRow>(`/api/v1/clients/${this.clientId}/document-requests`, { size: 50 }).subscribe((requests) => {
      this.requests = requests;
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

  format(value: unknown): string {
    return money(value);
  }

  onFile(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  onRequestFile(event: Event, requestId: string): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) {
      this.requestFiles[requestId] = file;
    }
  }

  upload(type: string): void {
    if (!this.file || !this.clientId) return;
    this.api.upload(`/api/v1/clients/${this.clientId}/documents`, this.file, { documentType: type }).subscribe(() => this.reload());
  }

  uploadRequest(req: DocumentRequestRow): void {
    const file = this.requestFiles[req.id];
    if (!file || !this.clientId) return;
    this.api.upload(`/api/v1/clients/${this.clientId}/document-requests/${req.id}/upload`, file).subscribe({
      next: () => {
        delete this.requestFiles[req.id];
        this.reload();
      }
    });
  }
}
