import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { ApiService } from '../../core/services/api.service';

@Component({
  standalone: true,
  imports: [FormsModule, MatCardModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule, MatTableModule],
  template: `
    <div class="page">
      <h1>Profit & Loss</h1>
      <div class="toolbar-row">
        <mat-form-field><mat-label>Client</mat-label>
          <mat-select [(ngModel)]="clientId">
            @for (c of clients; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <mat-form-field><mat-label>From</mat-label><input matInput type="date" [(ngModel)]="from"></mat-form-field>
        <mat-form-field><mat-label>To</mat-label><input matInput type="date" [(ngModel)]="to"></mat-form-field>
        <button mat-flat-button color="primary" (click)="load()">Run</button>
        <button mat-button (click)="exportFile('csv')">CSV</button>
        <button mat-button (click)="exportFile('xlsx')">XLSX</button>
      </div>
      @if (summary) {
        <div class="grid-2">
          <mat-card class="metric-card"><div class="label">Income</div><div class="value">{{ summary.totalIncome }}</div></mat-card>
          <mat-card class="metric-card"><div class="label">Expenses</div><div class="value">{{ summary.totalExpenses }}</div></mat-card>
          <mat-card class="metric-card"><div class="label">Net</div><div class="value">{{ summary.netResult }}</div></mat-card>
        </div>
      }
    </div>
  `
})
export class ReportsPage implements OnInit {
  clients: any[] = [];
  clientId = '';
  from = new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10);
  to = new Date().toISOString().slice(0, 10);
  summary: any;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.list<any>('/api/v1/clients').subscribe((clients) => {
      this.clients = clients;
      this.clientId = clients[0]?.id ?? '';
      this.load();
    });
  }

  load(): void {
    if (!this.clientId) return;
    this.api.get(`/api/v1/clients/${this.clientId}/reports/profit-and-loss`, { from: this.from, to: this.to }).subscribe((summary) => this.summary = summary);
  }

  exportFile(format: string): void {
    this.api.download(`/api/v1/clients/${this.clientId}/reports/profit-and-loss/export`, { from: this.from, to: this.to, format }).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `pl.${format}`;
      anchor.click();
      URL.revokeObjectURL(url);
    });
  }
}
