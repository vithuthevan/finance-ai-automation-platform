import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { ApiService } from '../../core/services/api.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { ToastService } from '../../shared/toast.service';
import { RouterLink } from '@angular/router';
import { ArCustomer, ArPayment, ArSummary, SalesInvoice } from './ar.models';
import { ArService } from './ar.service';

@Component({
  standalone: true,
  imports: [
    RouterLink, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatTabsModule,
    PageHeaderComponent
  ],
  template: `
    <div class="page">
      <app-page-header title="Invoicing & receivables" subtitle="Customers, sales invoices, payments, and AR ageing." />
      <mat-tab-group>
        <mat-tab label="Summary">
          @if (summary) {
            <div class="metrics">
              <div class="metric"><span>Outstanding</span><strong>{{ summary.totalOutstanding }}</strong></div>
              <div class="metric"><span>Overdue</span><strong>{{ summary.overdue }}</strong></div>
              <div class="metric"><span>Due this week</span><strong>{{ summary.dueThisWeek }}</strong></div>
              <div class="metric"><span>Collected (month)</span><strong>{{ summary.collectedThisMonth }}</strong></div>
            </div>
          }
        </mat-tab>
        <mat-tab label="Customers">
          <form [formGroup]="customerForm" class="inline-form" (ngSubmit)="createCustomer()">
            <mat-form-field appearance="outline"><mat-label>Name</mat-label><input matInput formControlName="name" /></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Email</mat-label><input matInput formControlName="email" /></mat-form-field>
            <button mat-flat-button color="primary" type="submit">Add customer</button>
          </form>
          <ul class="list">
            @for (c of customers; track c.id) {
              <li>{{ c.name }} — {{ c.email || 'no email' }}</li>
            }
          </ul>
        </mat-tab>
        <mat-tab label="Invoices">
          <form [formGroup]="invoiceForm" class="inline-form" (ngSubmit)="createInvoice()">
            <mat-form-field appearance="outline">
              <mat-label>Customer</mat-label>
              <mat-select formControlName="customerId">
                @for (c of customers; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Description</mat-label><input matInput formControlName="description" /></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Amount</mat-label><input matInput type="number" formControlName="amount" /></mat-form-field>
            <button mat-flat-button color="primary" type="submit">Create draft</button>
          </form>
          <ul class="list">
            @for (inv of invoices; track inv.id) {
              <li>
                {{ inv.invoiceNumber }} — {{ inv.documentStatus }} / {{ inv.settlementStatus }} — {{ inv.total }} {{ inv.currency }}
                @if (inv.documentStatus === 'DRAFT') {
                  <button mat-button type="button" (click)="issue(inv.id)">Issue</button>
                }
                @if (inv.documentStatus === 'ISSUED') {
                  <button mat-button type="button" (click)="downloadPdf(inv)">PDF</button>
                  <button mat-button type="button" (click)="send(inv.id)">Email</button>
                }
              </li>
            }
          </ul>
        </mat-tab>
        <mat-tab label="Payments">
          <form [formGroup]="paymentForm" class="inline-form" (ngSubmit)="recordPayment()">
            <mat-form-field appearance="outline">
              <mat-label>Customer</mat-label>
              <mat-select formControlName="customerId">
                @for (c of customers; track c.id) { <mat-option [value]="c.id">{{ c.name }}</mat-option> }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Amount</mat-label><input matInput type="number" formControlName="amount" /></mat-form-field>
            <button mat-flat-button color="primary" type="submit">Record payment</button>
          </form>
          <ul class="list">
            @for (p of payments; track p.id) {
              <li>
                {{ p.paymentDate }} — {{ p.amount }} (unallocated {{ p.unallocatedAmount }}) — {{ p.status }}
                <a mat-button [routerLink]="['/app/ar/payments', p.id]">Open</a>
              </li>
            }
          </ul>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; margin: 16px 0; }
    .metric { padding: 12px; border: 1px solid #e5e7eb; border-radius: 8px; display: flex; flex-direction: column; gap: 4px; }
    .inline-form { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin: 16px 0; }
    .list { list-style: none; padding: 0; margin: 0; }
    .list li { padding: 8px 0; border-bottom: 1px solid #f1f5f9; }
  `]
})
export class ArPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly arService = inject(ArService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  customers: ArCustomer[] = [];
  invoices: SalesInvoice[] = [];
  payments: ArPayment[] = [];
  summary: ArSummary | null = null;

  customerForm = this.fb.group({
    name: ['', Validators.required],
    email: ['', Validators.email]
  });

  invoiceForm = this.fb.group({
    customerId: ['', Validators.required],
    description: ['Services', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]]
  });

  paymentForm = this.fb.group({
    customerId: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]]
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.api.get<ArCustomer[]>('/api/v1/ar/customers').subscribe((c) => this.customers = c);
    this.api.list<SalesInvoice>('/api/v1/ar/invoices').subscribe((r) => this.invoices = r);
    this.api.get<ArPayment[]>('/api/v1/ar/payments').subscribe((p) => this.payments = p);
    this.api.get<ArSummary>('/api/v1/ar/receivables/summary').subscribe((s) => this.summary = s);
  }

  createCustomer(): void {
    if (this.customerForm.invalid) return;
    const v = this.customerForm.getRawValue();
    this.api.post<ArCustomer>('/api/v1/ar/customers', {
      name: v.name, email: v.email, paymentTermsDays: 30
    }).subscribe({
      next: () => { this.toast.success('Customer created'); this.customerForm.reset(); this.reload(); },
      error: (e) => this.toast.error(e?.error?.message || 'Failed')
    });
  }

  createInvoice(): void {
    if (this.invoiceForm.invalid) return;
    const v = this.invoiceForm.getRawValue();
    this.api.post<SalesInvoice>('/api/v1/ar/invoices', {
      customerId: v.customerId,
      lines: [{ description: v.description, quantity: 1, unitPrice: v.amount }]
    }).subscribe({
      next: () => { this.toast.success('Draft invoice created'); this.reload(); },
      error: (e) => this.toast.error(e?.error?.message || 'Failed')
    });
  }

  issue(id: string): void {
    this.api.post<SalesInvoice>(`/api/v1/ar/invoices/${id}/issue`, {}).subscribe({
      next: () => { this.toast.success('Invoice issued'); this.reload(); },
      error: (e) => this.toast.error(e?.error?.message || 'Failed')
    });
  }

  downloadPdf(inv: SalesInvoice): void {
    this.arService.downloadPdf(inv.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${inv.invoiceNumber}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (e) => this.toast.error(e?.error?.message || 'PDF download failed')
    });
  }

  send(id: string): void {
    this.api.post<void>(`/api/v1/ar/invoices/${id}/send`, {}).subscribe({
      next: () => this.toast.success('Invoice emailed'),
      error: (e) => this.toast.error(e?.error?.message || 'Failed')
    });
  }

  recordPayment(): void {
    if (this.paymentForm.invalid) return;
    const v = this.paymentForm.getRawValue();
    const today = new Date().toISOString().slice(0, 10);
    this.api.post<ArPayment>('/api/v1/ar/payments', {
      customerId: v.customerId, paymentDate: today, amount: v.amount, reference: 'Manual'
    }).subscribe({
      next: (p) => {
        this.toast.success('Payment recorded');
        this.paymentForm.reset();
        this.reload();
        if (p?.id) {
          window.location.href = `/app/ar/payments/${p.id}`;
        }
      },
      error: (e) => this.toast.error(e?.error?.message || 'Failed')
    });
  }
}
