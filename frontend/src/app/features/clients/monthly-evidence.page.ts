import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { finalize } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../shared/toast.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';

interface EvidenceItem {
  id: string;
  title: string;
  description: string | null;
  documentType: string;
  required: boolean;
  responsibleParty: 'CLIENT' | 'FIRM';
  active: boolean;
  sortOrder: number;
}

@Component({
  standalone: true,
  imports: [
    FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatCheckboxModule, PageHeaderComponent, LoadingStateComponent, EmptyStateComponent
  ],
  template: `
    <div class="page">
      <app-page-header
        title="Monthly evidence checklist"
        subtitle="Reusable items you expect from this client each month-end. Generate document requests in one click."
        backLink="/app/clients"
        backLabel="← Clients" />

      @if (!clientId) {
        <app-empty-state title="Select a client" description="Open this page from a client context or add ?clientId= to the URL." icon="assignment" />
      } @else if (loading) {
        <app-loading-state message="Loading checklist…" />
      } @else {
        <div class="toolbar-row">
          <mat-form-field appearance="outline">
            <mat-label>Period year</mat-label>
            <input matInput type="number" [(ngModel)]="genYear" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Month</mat-label>
            <mat-select [(ngModel)]="genMonth">
              @for (m of months; track m.value) {
                <mat-option [value]="m.value">{{ m.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <button mat-flat-button color="primary" type="button" (click)="generate()" [disabled]="generating || !items.length">
            Create requests for period
          </button>
        </div>

        @if (!items.length) {
          <app-empty-state
            title="No checklist items"
            description="Add the evidence you typically need each month (bank statement, rent invoice, sales summary, etc.)."
            icon="checklist" />
        }

        <form class="add-form" (ngSubmit)="addItem()">
          <mat-form-field appearance="outline">
            <mat-label>Title</mat-label>
            <input matInput [(ngModel)]="newTitle" name="title" required />
          </mat-form-field>
          <mat-form-field appearance="outline" class="span-2">
            <mat-label>Description (optional)</mat-label>
            <input matInput [(ngModel)]="newDescription" name="description" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Document type</mat-label>
            <mat-select [(ngModel)]="newDocType" name="docType">
              <mat-option value="BANK_STATEMENT">Bank statement</mat-option>
              <mat-option value="PURCHASE_INVOICE">Purchase invoice</mat-option>
              <mat-option value="SALES_INVOICE">Sales invoice</mat-option>
              <mat-option value="RECEIPT">Receipt</mat-option>
              <mat-option value="OTHER">Other</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-checkbox [(ngModel)]="newRequired" name="required">Required</mat-checkbox>
          <button mat-stroked-button type="submit" [disabled]="!newTitle.trim() || saving">Add item</button>
        </form>

        <div class="item-list">
          @for (item of items; track item.id) {
            <mat-card class="item-card">
              <div class="item-head">
                <strong>{{ item.required ? '✓ Required · ' : '' }}{{ item.title }}</strong>
                <span class="muted">{{ item.responsibleParty === 'CLIENT' ? 'Client provides' : 'Firm provides' }}</span>
              </div>
              @if (item.description) { <p class="muted">{{ item.description }}</p> }
            </mat-card>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .add-form { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin: 16px 0; }
    .span-2 { flex: 1 1 240px; }
    .item-list { display: flex; flex-direction: column; gap: 10px; margin-top: 16px; }
    .item-card { padding: 12px 16px !important; }
    .item-head { display: flex; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
    .muted { color: var(--fp-muted); font-size: 13px; }
  `]
})
export class MonthlyEvidencePage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);

  clientId = '';
  items: EvidenceItem[] = [];
  loading = false;
  saving = false;
  generating = false;
  newTitle = '';
  newDescription = '';
  newDocType = 'OTHER';
  newRequired = true;
  genYear = new Date().getFullYear();
  genMonth = new Date().getMonth() + 1;
  months = [
    { value: 1, label: 'January' }, { value: 2, label: 'February' }, { value: 3, label: 'March' },
    { value: 4, label: 'April' }, { value: 5, label: 'May' }, { value: 6, label: 'June' },
    { value: 7, label: 'July' }, { value: 8, label: 'August' }, { value: 9, label: 'September' },
    { value: 10, label: 'October' }, { value: 11, label: 'November' }, { value: 12, label: 'December' }
  ];

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((params) => {
      this.clientId = params.get('clientId') ?? '';
      if (this.clientId) {
        this.load();
      }
    });
  }

  load(): void {
    this.loading = true;
    this.api.get<EvidenceItem[]>(`/api/v1/clients/${this.clientId}/monthly-evidence`).pipe(
      finalize(() => this.loading = false)
    ).subscribe({
      next: (items) => this.items = items,
      error: () => this.toast.error('Could not load checklist')
    });
  }

  addItem(): void {
    if (!this.newTitle.trim()) {
      return;
    }
    this.saving = true;
    this.api.post<EvidenceItem>(`/api/v1/clients/${this.clientId}/monthly-evidence`, {
      title: this.newTitle.trim(),
      description: this.newDescription.trim() || null,
      documentType: this.newDocType,
      required: this.newRequired,
      responsibleParty: 'CLIENT'
    }).pipe(finalize(() => this.saving = false)).subscribe({
      next: (item) => {
        this.items = [...this.items, item];
        this.newTitle = '';
        this.newDescription = '';
        this.toast.success('Checklist item added');
      },
      error: () => this.toast.error('Could not add item')
    });
  }

  generate(): void {
    this.generating = true;
    this.api.post<{ created: number; skippedTitles: string[] }>(
      `/api/v1/clients/${this.clientId}/monthly-evidence/generate-requests`,
      { year: this.genYear, month: this.genMonth }
    ).pipe(finalize(() => this.generating = false)).subscribe({
      next: (res) => {
        const skipped = res.skippedTitles?.length ? ` Skipped: ${res.skippedTitles.join('; ')}` : '';
        this.toast.success(`Created ${res.created} request(s).${skipped}`);
      },
      error: () => this.toast.error('Could not generate requests')
    });
  }
}
