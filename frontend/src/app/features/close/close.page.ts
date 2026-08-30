import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../../core/services/api.service';

@Component({
  standalone: true,
  imports: [FormsModule, MatTableModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule],
  template: `
    <div class="page">
      <h1>Month-end close</h1>
      <div class="toolbar-row">
        <mat-form-field><mat-label>Client</mat-label>
          <mat-select [(ngModel)]="clientId" (selectionChange)="reload()">
            @for (c of clients; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
          </mat-select>
        </mat-form-field>
        <mat-form-field><mat-label>Year</mat-label><input matInput type="number" [(ngModel)]="year"></mat-form-field>
        <mat-form-field><mat-label>Month</mat-label><input matInput type="number" [(ngModel)]="month"></mat-form-field>
        <button mat-flat-button color="primary" (click)="openPeriod()">Open / review period</button>
      </div>
      <table mat-table [dataSource]="periods" class="full-width">
        <ng-container matColumnDef="period"><th mat-header-cell *matHeaderCellDef>Period</th><td mat-cell *matCellDef="let row">{{ row.year }}-{{ row.month }}</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th><td mat-cell *matCellDef="let row">{{ row.status }}</td></ng-container>
        <ng-container matColumnDef="ready"><th mat-header-cell *matHeaderCellDef>Readiness</th><td mat-cell *matCellDef="let row">{{ row.readinessPercent }}%</td></ng-container>
        <ng-container matColumnDef="blockers"><th mat-header-cell *matHeaderCellDef>Why not ready</th><td mat-cell *matCellDef="let row">{{ row.blockers?.join('; ') }}</td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            <button mat-button (click)="close(row)">Close</button>
            <button mat-button (click)="reopen(row)">Reopen</button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>
  `
})
export class ClosePage implements OnInit {
  clients: any[] = [];
  periods: any[] = [];
  clientId = '';
  year = new Date().getFullYear();
  month = new Date().getMonth() + 1;
  columns = ['period', 'status', 'ready', 'blockers', 'actions'];

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
    this.api.get<any[]>(`/api/v1/clients/${this.clientId}/periods`).subscribe((periods) => this.periods = periods);
  }

  openPeriod(): void {
    this.api.post(`/api/v1/clients/${this.clientId}/periods?year=${this.year}&month=${this.month}`).subscribe(() => this.reload());
  }

  close(row: any): void {
    this.api.post(`/api/v1/clients/${this.clientId}/periods/${row.id}/close`).subscribe({ next: () => this.reload(), error: () => this.reload() });
  }

  reopen(row: any): void {
    this.api.post(`/api/v1/clients/${this.clientId}/periods/${row.id}/reopen`, { reason: 'Adjustment required' }).subscribe(() => this.reload());
  }
}
