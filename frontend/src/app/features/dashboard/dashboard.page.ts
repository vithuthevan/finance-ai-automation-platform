import { Component, OnInit } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  standalone: true,
  imports: [MatCardModule, MatTableModule],
  template: `
    <div class="page">
      <h1>Dashboard</h1>
      <div class="grid-2">
        <mat-card class="metric-card"><div class="label">Clients</div><div class="value">{{ clients.length }}</div></mat-card>
        <mat-card class="metric-card"><div class="label">Assigned work</div><div class="value">{{ rows.length }}</div></mat-card>
      </div>
      <table mat-table [dataSource]="rows" class="full-width">
        <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Client</th><td mat-cell *matCellDef="let row">{{ row.name }}</td></ng-container>
        <ng-container matColumnDef="documents"><th mat-header-cell *matHeaderCellDef>Unreviewed</th><td mat-cell *matCellDef="let row">{{ row.unreviewedDocuments }}</td></ng-container>
        <ng-container matColumnDef="drafts"><th mat-header-cell *matHeaderCellDef>Drafts</th><td mat-cell *matCellDef="let row">{{ row.unapprovedTransactions }}</td></ng-container>
        <ng-container matColumnDef="missing"><th mat-header-cell *matHeaderCellDef>Unreconciled</th><td mat-cell *matCellDef="let row">{{ row.unreconciledBankEntries }}</td></ng-container>
        <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Status</th><td mat-cell *matCellDef="let row">{{ row.closeReadinessPercent }}% {{ row.closeBlockers?.length ? 'WAITING' : 'READY' }}</td></ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>
  `
})
export class DashboardPage implements OnInit {
  clients: any[] = [];
  rows: any[] = [];
  columns = ['name', 'documents', 'drafts', 'missing', 'status'];

  constructor(private api: ApiService, private auth: AuthService) {}

  ngOnInit(): void {
    const today = new Date();
    const from = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-01`;
    const to = today.toISOString().slice(0, 10);
    this.api.list<any>('/api/v1/clients').subscribe((clients) => {
      this.clients = clients;
      clients.forEach((client) => {
        this.api.get<any>(`/api/v1/clients/${client.id}/reports/dashboard`, { from, to }).subscribe((dash) => {
          this.rows = [...this.rows, { name: client.name, ...dash }];
        });
      });
    });
  }
}
