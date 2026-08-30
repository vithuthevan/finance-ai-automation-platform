import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule, RouterLink, MatTableModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule],
  template: `
    <div class="page">
      <h1>Income</h1>
      <div class="toolbar-row">
        <mat-form-field><mat-label>Client</mat-label>
          <mat-select [(ngModel)]="clientId" (selectionChange)="reload()">
            @for (c of clients; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
          </mat-select>
        </mat-form-field>
      </div>
      @if (auth.hasRole('ADMIN', 'ACCOUNTANT', 'BUSINESS_OWNER')) {
        <form class="toolbar-row" [formGroup]="form" (ngSubmit)="create()">
          <mat-form-field><mat-label>Date</mat-label><input matInput type="date" formControlName="transactionDate"></mat-form-field>
          <mat-form-field><mat-label>Category</mat-label>
            <mat-select formControlName="categoryId">
              @for (cat of categories; track cat.id) { <mat-option [value]="cat.id">{{ cat.name }}</mat-option> }
            </mat-select>
          </mat-form-field>
          <mat-form-field><mat-label>Amount</mat-label><input matInput type="number" formControlName="amount"></mat-form-field>
          <mat-form-field><mat-label>Customer</mat-label><input matInput formControlName="customerName"></mat-form-field>
          <mat-form-field><mat-label>Payment</mat-label>
            <mat-select formControlName="paymentMethod">
              <mat-option value="BANK_TRANSFER">Bank transfer</mat-option>
              <mat-option value="CASH">Cash</mat-option>
              <mat-option value="CARD">Card</mat-option>
            </mat-select>
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit">Create draft</button>
        </form>
      }
      <table mat-table [dataSource]="rows" class="full-width">
        <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Date</th><td mat-cell *matCellDef="let row">{{ row.transactionDate }}</td></ng-container>
        <ng-container matColumnDef="customer"><th mat-header-cell *matHeaderCellDef>Customer</th><td mat-cell *matCellDef="let row">{{ row.customerName }}</td></ng-container>
        <ng-container matColumnDef="amount"><th mat-header-cell *matHeaderCellDef>Amount</th><td mat-cell *matCellDef="let row">{{ row.amount }} {{ row.currencyCode }}</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th><td mat-cell *matCellDef="let row" [class]="'status-' + row.status">{{ row.status }}</td></ng-container>
        <ng-container matColumnDef="documents"><th mat-header-cell *matHeaderCellDef>Documents</th><td mat-cell *matCellDef="let row">{{ row.documentIds?.length || 0 }}</td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            @if (auth.hasRole('ADMIN', 'ACCOUNTANT') && row.status === 'DRAFT') {
              <button mat-button (click)="approve(row)">Approve</button>
            }
            @if (auth.hasRole('ADMIN', 'ACCOUNTANT') && row.status === 'APPROVED') {
              <button mat-button (click)="voidRow(row)">Void</button>
            }
            <button mat-button (click)="select(row)">Documents</button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (selected) {
        <h2>Supporting documents</h2>
        <div class="toolbar-row">
          @for (doc of supporting; track doc.id) {
            <span>{{ doc.fileName }} · {{ doc.documentType }} · {{ doc.uploadedAt }}</span>
            <a mat-button [routerLink]="['/app/documents', clientId, doc.id]">Preview</a>
            <button mat-button (click)="download(doc)">Download</button>
          }
        </div>
        @if (auth.hasRole('ADMIN', 'ACCOUNTANT') && selected.status !== 'VOID') {
          <div class="toolbar-row">
            <mat-form-field>
              <mat-label>Attach document</mat-label>
              <mat-select [(ngModel)]="attachDocumentId">
                @for (doc of unlinked; track doc.id) { <mat-option [value]="doc.id">{{ doc.fileName }}</mat-option> }
              </mat-select>
            </mat-form-field>
            <button mat-stroked-button (click)="attach()" [disabled]="!attachDocumentId">Link document</button>
          </div>
        }
      }
    </div>
  `
})
export class IncomePage implements OnInit {
  clients: any[] = [];
  categories: any[] = [];
  rows: any[] = [];
  supporting: any[] = [];
  unlinked: any[] = [];
  selected: any = null;
  attachDocumentId = '';
  clientId = '';
  columns = ['date', 'customer', 'amount', 'status', 'documents', 'actions'];
  form = this.fb.nonNullable.group({
    transactionDate: ['', Validators.required],
    categoryId: ['', Validators.required],
    amount: [0, Validators.required],
    customerName: ['', Validators.required],
    paymentMethod: ['BANK_TRANSFER', Validators.required]
  });

  constructor(private api: ApiService, private fb: FormBuilder, readonly auth: AuthService) {}

  ngOnInit(): void {
    this.api.list<any>('/api/v1/clients').subscribe((clients) => {
      this.clients = clients;
      this.clientId = clients[0]?.id ?? '';
      this.reload();
    });
    this.api.get<any[]>('/api/v1/categories', { categoryType: 'INCOME' }).subscribe((cats) => this.categories = cats);
  }

  reload(): void {
    if (!this.clientId) return;
    this.api.get<any>(`/api/v1/clients/${this.clientId}/income`, { size: 50 }).subscribe((page) => this.rows = page.content ?? []);
  }

  create(): void {
    this.api.post(`/api/v1/clients/${this.clientId}/income`, this.form.getRawValue()).subscribe(() => this.reload());
  }

  approve(row: any): void {
    this.api.post(`/api/v1/clients/${this.clientId}/income/${row.id}/approve`).subscribe(() => this.reload());
  }

  voidRow(row: any): void {
    this.api.post(`/api/v1/clients/${this.clientId}/income/${row.id}/void`, { reason: 'Voided from workspace' }).subscribe(() => this.reload());
  }

  select(row: any): void {
    this.selected = row;
    this.supporting = [];
    (row.documentIds ?? []).forEach((id: string) => {
      this.api.get<any>(`/api/v1/clients/${this.clientId}/documents/${id}`).subscribe((doc) => {
        this.supporting = [...this.supporting, doc];
      });
    });
    this.api.get<any>(`/api/v1/clients/${this.clientId}/documents`, { linked: false, size: 50 }).subscribe((page) => {
      this.unlinked = page.content ?? [];
    });
  }

  attach(): void {
    if (!this.selected || !this.attachDocumentId) {
      return;
    }
    this.api.post(`/api/v1/clients/${this.clientId}/income/${this.selected.id}/documents`, {
      documentId: this.attachDocumentId
    }).subscribe(() => {
      this.reload();
      this.api.get<any>(`/api/v1/clients/${this.clientId}/income/${this.selected.id}`).subscribe((row) => this.select(row));
    });
  }

  download(doc: any): void {
    this.api.download(`/api/v1/clients/${this.clientId}/documents/${doc.id}/content`).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = doc.fileName || 'document';
      anchor.click();
      URL.revokeObjectURL(url);
    });
  }
}
