import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { ErrorStateComponent } from '../../shared/ui/error-state.component';

interface AuditRow {
  id: string;
  occurredAt: string;
  actorRole: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  clientId?: string;
  outcome?: string;
}

@Component({
  standalone: true,
  imports: [
    DatePipe, MatTableModule, MatButtonModule, PageHeaderComponent, StatusBadgeComponent,
    LoadingStateComponent, EmptyStateComponent, ErrorStateComponent
  ],
  template: `
    <div class="page">
      <app-page-header
        title="Audit log"
        subtitle="When something changed, who did it, and whether it succeeded — newest first." />

      @if (loadError) {
        <app-error-state title="Could not load audit events" [message]="loadError" (retry)="load()" />
      } @else if (loading) {
        <app-loading-state message="Loading audit events…" />
      } @else if (!rows.length) {
        <app-empty-state
          title="No audit events yet"
          description="Activity across your firm will appear here as users work in the platform."
          icon="policy" />
      } @else {
        <div class="table-scroll">
          <table mat-table [dataSource]="rows" class="full-width data-table audit-table">
            <ng-container matColumnDef="when">
              <th mat-header-cell *matHeaderCellDef>When</th>
              <td mat-cell *matCellDef="let row">{{ row.occurredAt | date:'medium' }}</td>
            </ng-container>
            <ng-container matColumnDef="who">
              <th mat-header-cell *matHeaderCellDef>Who</th>
              <td mat-cell *matCellDef="let row">{{ roleLabel(row.actorRole) }}</td>
            </ng-container>
            <ng-container matColumnDef="what">
              <th mat-header-cell *matHeaderCellDef>What</th>
              <td mat-cell *matCellDef="let row" class="audit-what">{{ actionLabel(row.action) }}</td>
            </ng-container>
            <ng-container matColumnDef="context">
              <th mat-header-cell *matHeaderCellDef>Context</th>
              <td mat-cell *matCellDef="let row">{{ resourceLabel(row.resourceType) }}</td>
            </ng-container>
            <ng-container matColumnDef="result">
              <th mat-header-cell *matHeaderCellDef>Result</th>
              <td mat-cell *matCellDef="let row">
                @if (row.outcome) {
                  <app-status-badge [status]="outcomeStatus(row.outcome)" [label]="row.outcome" />
                } @else {
                  <span class="hint">—</span>
                }
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        </div>
        <div class="toolbar-row">
          <button mat-stroked-button type="button" (click)="prev()" [disabled]="page === 0">Previous</button>
          <span>Page {{ page + 1 }}</span>
          <button mat-stroked-button type="button" (click)="next()" [disabled]="(page + 1) * size >= total">Next</button>
        </div>
      }
    </div>
  `,
  styles: [`
    .audit-what { max-width: 280px; word-break: break-word; }
    @media (max-width: 768px) {
      .audit-table .mat-column-context { display: none; }
    }
  `]
})
export class AuditPage implements OnInit {
  private readonly api = inject(ApiService);

  rows: AuditRow[] = [];
  columns = ['when', 'who', 'what', 'context', 'result'];
  loading = false;
  loadError = '';
  page = 0;
  size = 50;
  total = 0;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.loadError = '';
    this.api.get<any>('/api/v1/audit', { page: this.page, size: this.size }).pipe(
      finalize(() => this.loading = false)
    ).subscribe({
      next: (page) => {
        this.rows = page.content ?? [];
        this.total = page.totalElements ?? 0;
      },
      error: () => {
        this.rows = [];
        this.loadError = 'Unable to load the audit log.';
      }
    });
  }

  prev(): void {
    if (this.page === 0) {
      return;
    }
    this.page -= 1;
    this.load();
  }

  next(): void {
    if ((this.page + 1) * this.size >= this.total) {
      return;
    }
    this.page += 1;
    this.load();
  }

  roleLabel(role: string): string {
    return role?.replace(/_/g, ' ') ?? '—';
  }

  actionLabel(action: string): string {
    return action?.replace(/_/g, ' ') ?? '—';
  }

  resourceLabel(type: string): string {
    return type?.replace(/_/g, ' ') ?? '—';
  }

  outcomeStatus(outcome: string): string {
    const upper = outcome.toUpperCase();
    if (upper.includes('FAIL') || upper.includes('DENIED')) {
      return 'FAILED';
    }
    if (upper.includes('SUCCESS') || upper === 'OK') {
      return 'COMPLETED';
    }
    return 'PENDING';
  }
}
