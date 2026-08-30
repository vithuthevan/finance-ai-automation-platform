import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  standalone: true,
  imports: [FormsModule, MatCardModule, MatButtonModule, MatFormFieldModule, MatSelectModule],
  template: `
    <div class="page">
      <h1>Send documents to your accountant</h1>
      <div class="toolbar-row">
        <mat-form-field><mat-label>Business</mat-label>
          <mat-select [(ngModel)]="clientId">
            @for (c of clients; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <input type="file" (change)="onFile($event)">
        <button mat-flat-button color="primary" (click)="upload('RECEIPT')">Upload receipt</button>
        <button mat-stroked-button (click)="upload('PURCHASE_INVOICE')">Upload invoice</button>
        <button mat-stroked-button (click)="upload('BANK_STATEMENT')">Upload bank statement</button>
      </div>
      <h2>What your accountant still needs</h2>
      @for (req of requests; track req.id) {
        <mat-card class="metric-card">
          <div class="label">{{ req.documentType }} · {{ req.status }}</div>
          <div>{{ req.description }}</div>
        </mat-card>
      }
      @if (!auth.isUploadOnly()) {
        <h2>Approved totals</h2>
        @if (summary) {
          <div class="grid-2">
            <mat-card class="metric-card"><div class="label">Income</div><div class="value">{{ summary.totalIncome }}</div></mat-card>
            <mat-card class="metric-card"><div class="label">Expenses</div><div class="value">{{ summary.totalExpenses }}</div></mat-card>
          </div>
        }
      }
    </div>
  `
})
export class OwnerPage implements OnInit {
  clients: any[] = [];
  requests: any[] = [];
  summary: any;
  clientId = '';
  file: File | null = null;

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
    this.api.get<any[]>(`/api/v1/clients/${this.clientId}/document-requests`).subscribe((requests) => this.requests = requests);
    if (this.auth.isUploadOnly()) {
      this.summary = null;
      return;
    }
    const from = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10);
    const to = new Date().toISOString().slice(0, 10);
    this.api.get(`/api/v1/clients/${this.clientId}/reports/profit-and-loss`, { from, to }).subscribe({
      next: (summary) => this.summary = summary,
      error: () => this.summary = null
    });
  }

  onFile(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  upload(type: string): void {
    if (!this.file || !this.clientId) return;
    this.api.upload(`/api/v1/clients/${this.clientId}/documents`, this.file, { documentType: type }).subscribe(() => this.reload());
  }
}
