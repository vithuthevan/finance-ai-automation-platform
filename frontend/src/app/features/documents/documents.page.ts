import { Component, OnInit } from '@angular/core';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, MatTableModule, MatFormFieldModule, MatSelectModule, MatButtonModule, MatInputModule],
  template: `
    <div class="page">
      <h1>{{ auth.isUploadOnly() ? 'My documents' : 'Document inbox' }}</h1>

      @if (auth.canUploadDocuments()) {
        <section class="card-block">
          <h2>Upload document</h2>
          <div class="toolbar-row">
            @if (!lockClient) {
              <mat-form-field>
                <mat-label>Client</mat-label>
                <mat-select [(ngModel)]="uploadClientId">
                  @for (c of clients; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
                </mat-select>
              </mat-form-field>
            }
            <mat-form-field>
              <mat-label>Document type</mat-label>
              <mat-select [(ngModel)]="uploadType">
                <mat-option value="RECEIPT">Receipt</mat-option>
                <mat-option value="PURCHASE_INVOICE">Purchase invoice</mat-option>
                <mat-option value="SALES_INVOICE">Sales invoice</mat-option>
                <mat-option value="CREDIT_NOTE">Credit note</mat-option>
                <mat-option value="BANK_STATEMENT">Bank statement</mat-option>
                <mat-option value="OTHER">Other</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field>
              <mat-label>Note</mat-label>
              <input matInput [(ngModel)]="uploadNote">
            </mat-form-field>
            <input type="file" accept=".pdf,.jpg,.jpeg,.png,.webp,.csv" (change)="onFile($event)">
            <button mat-flat-button color="primary" (click)="upload(false)" [disabled]="!file || !uploadClientId">Upload</button>
          </div>
          @if (uploadMessage) { <p class="hint">{{ uploadMessage }}</p> }
          @if (duplicateId) {
            <p class="hint">This file was uploaded before.
              <button mat-button (click)="upload(true)">Upload anyway</button>
            </p>
          }
        </section>
      }

      <div class="toolbar-row">
        @if (!lockClient) {
          <mat-form-field>
            <mat-label>Client</mat-label>
            <mat-select [(ngModel)]="clientId" (selectionChange)="reload()">
              <mat-option value="">All assigned</mat-option>
              @for (c of clients; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
            </mat-select>
          </mat-form-field>
        }
        <mat-form-field>
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="status" (selectionChange)="reload()">
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
        <mat-form-field>
          <mat-label>Type</mat-label>
          <mat-select [(ngModel)]="documentType" (selectionChange)="reload()">
            <mat-option value="">All</mat-option>
            <mat-option value="RECEIPT">Receipt</mat-option>
            <mat-option value="PURCHASE_INVOICE">Purchase invoice</mat-option>
            <mat-option value="SALES_INVOICE">Sales invoice</mat-option>
            <mat-option value="CREDIT_NOTE">Credit note</mat-option>
            <mat-option value="BANK_STATEMENT">Bank statement</mat-option>
            <mat-option value="OTHER">Other</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Linked</mat-label>
          <mat-select [(ngModel)]="linked" (selectionChange)="reload()">
            <mat-option value="">All</mat-option>
            <mat-option value="true">Linked</mat-option>
            <mat-option value="false">Unlinked</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field>
          <mat-label>From</mat-label>
          <input matInput type="date" [(ngModel)]="from" (change)="reload()">
        </mat-form-field>
        <mat-form-field>
          <mat-label>To</mat-label>
          <input matInput type="date" [(ngModel)]="to" (change)="reload()">
        </mat-form-field>
      </div>

      <table mat-table [dataSource]="docs" class="full-width">
        <ng-container matColumnDef="fileName">
          <th mat-header-cell *matHeaderCellDef>Document</th>
          <td mat-cell *matCellDef="let row">
            <a [routerLink]="['/app/documents', row.clientId, row.id]">{{ row.fileName }}</a>
          </td>
        </ng-container>
        <ng-container matColumnDef="clientName"><th mat-header-cell *matHeaderCellDef>Client</th><td mat-cell *matCellDef="let row">{{ row.clientName }}</td></ng-container>
        <ng-container matColumnDef="documentType"><th mat-header-cell *matHeaderCellDef>Type</th><td mat-cell *matCellDef="let row">{{ row.documentType }}</td></ng-container>
        <ng-container matColumnDef="uploadedByName"><th mat-header-cell *matHeaderCellDef>Uploaded by</th><td mat-cell *matCellDef="let row">{{ row.uploadedByName }}</td></ng-container>
        <ng-container matColumnDef="uploadedAt"><th mat-header-cell *matHeaderCellDef>Uploaded</th><td mat-cell *matCellDef="let row">{{ row.uploadedAt | date:'medium' }}</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th><td mat-cell *matCellDef="let row" [class]="'status-' + row.status">{{ row.status }}</td></ng-container>
        <ng-container matColumnDef="linked">
          <th mat-header-cell *matHeaderCellDef>Linked transaction</th>
          <td mat-cell *matCellDef="let row">{{ linkedLabel(row) }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            <a mat-button [routerLink]="['/app/documents', row.clientId, row.id]">Review</a>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      <div class="toolbar-row">
        <button mat-stroked-button (click)="prev()" [disabled]="page === 0">Previous</button>
        <span>Page {{ page + 1 }} · {{ total }} documents</span>
        <button mat-stroked-button (click)="next()" [disabled]="(page + 1) * size >= total">Next</button>
      </div>
    </div>
  `
})
export class DocumentsPage implements OnInit {
  clients: any[] = [];
  docs: any[] = [];
  clientId = '';
  uploadClientId = '';
  status = '';
  documentType = '';
  linked = '';
  from = '';
  to = '';
  uploadType = 'RECEIPT';
  uploadNote = '';
  uploadMessage = '';
  duplicateId = '';
  file: File | null = null;
  lockClient = false;
  page = 0;
  size = 20;
  total = 0;
  columns = ['fileName', 'clientName', 'documentType', 'uploadedByName', 'uploadedAt', 'status', 'linked', 'actions'];

  constructor(private api: ApiService, readonly auth: AuthService, private route: ActivatedRoute) {}

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
      this.lockClient = this.auth.hasRole('BUSINESS_OWNER') && clients.length === 1;
      if (preferredClient && clients.some((client) => client.id === preferredClient)) {
        this.clientId = preferredClient;
        this.uploadClientId = preferredClient;
      } else if (this.lockClient) {
        this.clientId = clients[0].id;
        this.uploadClientId = clients[0].id;
      } else if (this.auth.hasRole('BUSINESS_OWNER') && clients.length > 0) {
        this.uploadClientId = clients[0].id;
      }
      this.reload();
    });
  }

  reload(): void {
    this.api.get<any>('/api/v1/documents', {
      clientId: this.clientId || undefined,
      status: this.status || undefined,
      documentType: this.documentType || undefined,
      from: this.from || undefined,
      to: this.to || undefined,
      linked: this.linked === '' ? undefined : this.linked,
      page: this.page,
      size: this.size
    }).subscribe((page) => {
      this.docs = page.content ?? [];
      this.total = page.totalElements ?? 0;
    });
  }

  onFile(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.uploadMessage = '';
    this.duplicateId = '';
  }

  upload(allowDuplicate: boolean): void {
    if (!this.file || !this.uploadClientId) {
      return;
    }
    this.api.upload<any>(`/api/v1/clients/${this.uploadClientId}/documents`, this.file, {
      documentType: this.uploadType,
      description: this.uploadNote,
      allowDuplicate: String(allowDuplicate)
    }).subscribe({
      next: (doc) => {
        this.uploadMessage = doc.possibleDuplicate
          ? 'Uploaded with a duplicate warning.'
          : 'Document uploaded.';
        this.duplicateId = '';
        this.file = null;
        this.reload();
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 409 || err.error?.errorCode === 'POSSIBLE_DUPLICATE') {
          this.duplicateId = err.error?.existingDocumentId ?? '';
          this.uploadMessage = 'Possible duplicate of an existing document.';
          return;
        }
        this.uploadMessage = err.error?.detail || 'Upload failed.';
      }
    });
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
