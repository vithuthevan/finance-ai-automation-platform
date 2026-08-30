import { Component, OnInit } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { ApiService } from '../../core/services/api.service';

@Component({
  standalone: true,
  imports: [MatTableModule],
  template: `
    <div class="page">
      <h1>Audit log</h1>
      <table mat-table [dataSource]="rows" class="full-width">
        <ng-container matColumnDef="occurredAt"><th mat-header-cell *matHeaderCellDef>When</th><td mat-cell *matCellDef="let row">{{ row.occurredAt }}</td></ng-container>
        <ng-container matColumnDef="action"><th mat-header-cell *matHeaderCellDef>Action</th><td mat-cell *matCellDef="let row">{{ row.action }}</td></ng-container>
        <ng-container matColumnDef="resource"><th mat-header-cell *matHeaderCellDef>Resource</th><td mat-cell *matCellDef="let row">{{ row.resourceType }}</td></ng-container>
        <ng-container matColumnDef="actor"><th mat-header-cell *matHeaderCellDef>Actor</th><td mat-cell *matCellDef="let row">{{ row.actorRole }}</td></ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>
  `
})
export class AuditPage implements OnInit {
  rows: any[] = [];
  columns = ['occurredAt', 'action', 'resource', 'actor'];

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<any>('/api/v1/audit', { size: 50 }).subscribe((page) => this.rows = page.content ?? []);
  }
}
