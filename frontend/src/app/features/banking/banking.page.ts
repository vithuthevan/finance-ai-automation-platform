import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ApiService } from '../../core/services/api.service';

@Component({
  standalone: true,
  imports: [FormsModule, MatTableModule, MatFormFieldModule, MatSelectModule, MatButtonModule, MatCardModule],
  template: `
    <div class="page">
      <h1>Banking</h1>
      <div class="toolbar-row">
        <mat-form-field><mat-label>Client</mat-label>
          <mat-select [(ngModel)]="clientId" (selectionChange)="reload()">
            @for (c of clients; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <input type="file" accept=".csv" (change)="onFile($event)">
        <button mat-flat-button color="primary" (click)="importCsv()">Import CSV</button>
      </div>
      <div class="grid-2">
        @for (key of keys; track key) {
          <mat-card class="metric-card"><div class="label">{{ key }}</div><div class="value">{{ counts[key] || 0 }}</div></mat-card>
        }
      </div>
      <table mat-table [dataSource]="rows" class="full-width">
        <ng-container matColumnDef="date"><th mat-header-cell *matHeaderCellDef>Date</th><td mat-cell *matCellDef="let row">{{ row.txnDate }}</td></ng-container>
        <ng-container matColumnDef="description"><th mat-header-cell *matHeaderCellDef>Description</th><td mat-cell *matCellDef="let row">{{ row.description }}</td></ng-container>
        <ng-container matColumnDef="debit"><th mat-header-cell *matHeaderCellDef>Debit</th><td mat-cell *matCellDef="let row">{{ row.debit }}</td></ng-container>
        <ng-container matColumnDef="credit"><th mat-header-cell *matHeaderCellDef>Credit</th><td mat-cell *matCellDef="let row">{{ row.credit }}</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th><td mat-cell *matCellDef="let row">{{ row.matchStatus }}</td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            <button mat-button (click)="ignore(row)">Ignore</button>
            <button mat-button (click)="missing(row)">Missing receipt</button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>
  `
})
export class BankingPage implements OnInit {
  clients: any[] = [];
  rows: any[] = [];
  counts: Record<string, number> = {};
  keys = ['UNMATCHED', 'SUGGESTED', 'MATCHED', 'MISSING_RECEIPT', 'IGNORED'];
  clientId = '';
  file: File | null = null;
  columns = ['date', 'description', 'debit', 'credit', 'status', 'actions'];

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.list<any>('/api/v1/clients').subscribe((clients) => {
      this.clients = clients;
      this.clientId = clients[0]?.id ?? '';
      this.reload();
    });
  }

  reload(): void {
    if (!this.clientId) return;
    this.api.get<any>(`/api/v1/clients/${this.clientId}/bank/transactions`, { size: 50 }).subscribe((page) => this.rows = page.content ?? []);
    this.api.get<Record<string, number>>(`/api/v1/clients/${this.clientId}/bank/dashboard`).subscribe((counts) => this.counts = counts);
  }

  onFile(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  importCsv(): void {
    if (!this.file || !this.clientId) return;
    this.api.upload(`/api/v1/clients/${this.clientId}/bank/imports`, this.file).subscribe(() => this.reload());
  }

  ignore(row: any): void {
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/status`, { status: 'IGNORED' }).subscribe(() => this.reload());
  }

  missing(row: any): void {
    this.api.post(`/api/v1/clients/${this.clientId}/bank/transactions/${row.id}/status`, { status: 'MISSING_RECEIPT' }).subscribe(() => this.reload());
  }
}
