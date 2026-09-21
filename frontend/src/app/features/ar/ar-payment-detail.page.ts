import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { AuthService } from '../../core/auth/auth.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { ToastService } from '../../shared/toast.service';
import { ArCustomer, ArPayment, SalesInvoice } from './ar.models';
import { ArService } from './ar.service';
import { ApiService } from '../../core/services/api.service';

interface AllocationRow {
  invoice: SalesInvoice;
  selected: boolean;
  amount: number;
}

@Component({
  standalone: true,
  imports: [
    RouterLink, ReactiveFormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule,
    MatTableModule, PageHeaderComponent
  ],
  template: `
    <div class="page">
      <app-page-header [title]="paymentTitle()" subtitle="Allocate receipts to issued invoices.">
        <a fpPageActions mat-stroked-button routerLink="/app/ar">Back to AR</a>
      </app-page-header>

      @if (payment()) {
        <section class="summary card-block">
          <div><strong>Reference:</strong> {{ payment()!.reference || '—' }}</div>
          <div><strong>Date:</strong> {{ payment()!.paymentDate }}</div>
          <div><strong>Amount:</strong> {{ payment()!.amount }}</div>
          <div><strong>Allocated:</strong> {{ allocatedTotal() }}</div>
          <div><strong>Available:</strong> {{ payment()!.unallocatedAmount }}</div>
          <div><strong>Status:</strong> {{ payment()!.status }}</div>
          <div><strong>Source:</strong> {{ payment()!.source }}</div>
        </section>

        <h2>Allocation history</h2>
        <table mat-table [dataSource]="payment()!.allocations" class="full-width">
          <ng-container matColumnDef="invoice"><th mat-header-cell *matHeaderCellDef>Invoice</th><td mat-cell *matCellDef="let a">{{ a.invoiceNumber }}</td></ng-container>
          <ng-container matColumnDef="amount"><th mat-header-cell *matHeaderCellDef>Amount</th><td mat-cell *matCellDef="let a">{{ a.amount }}</td></ng-container>
          <ng-container matColumnDef="active"><th mat-header-cell *matHeaderCellDef>Status</th><td mat-cell *matCellDef="let a">{{ a.active ? 'Active' : 'Reversed' }}</td></ng-container>
          <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let a">
              @if (a.active && canOperate) {
                <button mat-button type="button" (click)="reverseAllocation(a.id)">Reverse</button>
              }
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="historyColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: historyColumns"></tr>
        </table>

        @if (canOperate && payment()!.status !== 'REVERSED' && payment()!.unallocatedAmount > 0) {
          <h2>Allocate to invoices</h2>
          <table mat-table [dataSource]="eligibleRows()" class="full-width">
            <ng-container matColumnDef="pick"><th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let row">
                <mat-checkbox [checked]="row.selected" (change)="toggleRow(row, $event.checked)"></mat-checkbox>
              </td>
            </ng-container>
            <ng-container matColumnDef="number"><th mat-header-cell *matHeaderCellDef>Invoice</th><td mat-cell *matCellDef="let row">{{ row.invoice.invoiceNumber }}</td></ng-container>
            <ng-container matColumnDef="customer"><th mat-header-cell *matHeaderCellDef>Customer</th><td mat-cell *matCellDef="let row">{{ row.invoice.customerName }}</td></ng-container>
            <ng-container matColumnDef="issue"><th mat-header-cell *matHeaderCellDef>Issue</th><td mat-cell *matCellDef="let row">{{ row.invoice.issueDate }}</td></ng-container>
            <ng-container matColumnDef="due"><th mat-header-cell *matHeaderCellDef>Due</th><td mat-cell *matCellDef="let row">{{ row.invoice.dueDate }}</td></ng-container>
            <ng-container matColumnDef="total"><th mat-header-cell *matHeaderCellDef>Total</th><td mat-cell *matCellDef="let row">{{ row.invoice.total }}</td></ng-container>
            <ng-container matColumnDef="outstanding"><th mat-header-cell *matHeaderCellDef>Outstanding</th><td mat-cell *matCellDef="let row">{{ row.invoice.outstanding }}</td></ng-container>
            <ng-container matColumnDef="alloc"><th mat-header-cell *matHeaderCellDef>Allocation</th>
              <td mat-cell *matCellDef="let row">
                <mat-form-field appearance="outline" class="compact">
                  <input matInput type="number" [disabled]="!row.selected" [value]="row.amount" (change)="onAmountChange(row, $event)" />
                </mat-form-field>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="wizardColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: wizardColumns"></tr>
          </table>

          <div class="totals">
            <div>Payment: {{ payment()!.amount }}</div>
            <div>Already allocated: {{ allocatedTotal() }}</div>
            <div>New allocation: {{ newAllocationTotal() }}</div>
            <div>Remaining unapplied: {{ remainingAfterNew() }}</div>
          </div>
          <button mat-flat-button color="primary" type="button" (click)="submitAllocations()" [disabled]="newAllocationTotal() <= 0">Apply allocations</button>
        }

        @if (canOperate && payment()!.status !== 'REVERSED') {
          <section class="danger-zone">
            <h2>Reverse payment</h2>
            <p>Reversing returns allocated amounts to invoice outstanding balances.</p>
            <mat-form-field appearance="outline" class="span-2">
              <mat-label>Reason</mat-label>
              <input matInput [formControl]="reverseReason" />
            </mat-form-field>
            <button mat-stroked-button color="warn" type="button" (click)="reversePayment()" [disabled]="reverseReason.invalid">Reverse entire payment</button>
          </section>
        }
      }
    </div>
  `,
  styles: [`
    .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px; margin-bottom: 16px; }
    .card-block { padding: 12px; border: 1px solid #e5e7eb; border-radius: 8px; }
    .totals { margin: 16px 0; display: grid; gap: 4px; }
    .danger-zone { margin-top: 32px; padding-top: 16px; border-top: 1px solid #fecaca; }
    .full-width { width: 100%; }
    .compact { width: 120px; }
  `]
})
export class ArPaymentDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly ar = inject(ArService);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  payment = signal<ArPayment | null>(null);
  customers = signal<ArCustomer[]>([]);
  private rows = signal<AllocationRow[]>([]);

  historyColumns = ['invoice', 'amount', 'active', 'actions'];
  wizardColumns = ['pick', 'number', 'customer', 'issue', 'due', 'total', 'outstanding', 'alloc'];
  reverseReason = new FormControl('', { nonNullable: true, validators: [Validators.required] });

  eligibleRows = computed(() => this.rows());

  get canOperate(): boolean {
    return this.auth.hasRole('ADMIN', 'ACCOUNTANT');
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('paymentId');
    if (!id) return;
    this.api.get<ArCustomer[]>('/api/v1/ar/customers').subscribe((c) => this.customers.set(c));
    this.reload(id);
  }

  paymentTitle(): string {
    const p = this.payment();
    return p ? `Payment ${p.reference || p.id.slice(0, 8)}` : 'Payment';
  }

  allocatedTotal(): number {
    const p = this.payment();
    if (!p) return 0;
    return p.allocations.filter((a) => a.active).reduce((sum, a) => sum + a.amount, 0);
  }

  newAllocationTotal(): number {
    return this.rows().filter((r) => r.selected).reduce((sum, r) => sum + (r.amount || 0), 0);
  }

  remainingAfterNew(): number {
    const p = this.payment();
    if (!p) return 0;
    return Math.max(0, p.unallocatedAmount - this.newAllocationTotal());
  }

  reload(id: string): void {
    this.ar.getPayment(id).subscribe({
      next: (p) => {
        this.payment.set(p);
        this.loadEligibleInvoices(p);
      },
      error: (e) => this.toast.error(e?.error?.message || 'Unable to load payment')
    });
  }

  loadEligibleInvoices(payment: ArPayment): void {
    const params: Record<string, string> = { documentStatus: 'ISSUED', size: '100' };
    if (payment.customerId) {
      params['customerId'] = payment.customerId;
    }
    this.api.list<SalesInvoice>('/api/v1/ar/invoices', params).subscribe((invoices) => {
      const eligible = invoices.filter((inv) => inv.outstanding > 0);
      this.rows.set(eligible.map((invoice) => ({
        invoice,
        selected: false,
        amount: Math.min(invoice.outstanding, payment.unallocatedAmount)
      })));
    });
  }

  toggleRow(row: AllocationRow, selected: boolean): void {
    const p = this.payment();
    if (!p) return;
    row.selected = selected;
    if (selected) {
      const remaining = p.unallocatedAmount - this.newAllocationTotal();
      row.amount = Math.min(row.invoice.outstanding, Math.max(0, remaining + row.amount));
    }
    this.rows.set([...this.rows()]);
  }

  onAmountChange(row: AllocationRow, event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    row.amount = Math.min(row.invoice.outstanding, Math.max(0, value));
    this.rows.set([...this.rows()]);
  }

  submitAllocations(): void {
    const p = this.payment();
    if (!p) return;
    const allocations = this.rows()
      .filter((r) => r.selected && r.amount > 0)
      .map((r) => ({ invoiceId: r.invoice.id, amount: r.amount }));
    if (!allocations.length) {
      this.toast.error('Select at least one invoice.');
      return;
    }
    if (this.newAllocationTotal() > p.unallocatedAmount) {
      this.toast.error('Allocation exceeds available payment amount.');
      return;
    }
    this.ar.allocate(p.id, allocations).subscribe({
      next: (updated) => {
        this.toast.success('Allocations applied');
        this.payment.set(updated);
        this.loadEligibleInvoices(updated);
      },
      error: (e) => this.toast.error(e?.error?.message || e?.error?.detail || 'Allocation failed')
    });
  }

  reverseAllocation(allocationId: string): void {
    const p = this.payment();
    if (!p || !confirm('Reverse this allocation? Invoice outstanding will be restored.')) return;
    const reason = prompt('Optional reason') || undefined;
    this.ar.reverseAllocation(p.id, allocationId, reason).subscribe({
      next: (updated) => { this.toast.success('Allocation reversed'); this.payment.set(updated); this.loadEligibleInvoices(updated); },
      error: (e) => this.toast.error(e?.error?.message || 'Failed')
    });
  }

  reversePayment(): void {
    const p = this.payment();
    if (!p || !confirm('Reverse the entire payment? All allocations will be removed and invoice balances restored.')) return;
    this.ar.reversePayment(p.id, this.reverseReason.value).subscribe({
      next: (updated) => { this.toast.success('Payment reversed'); this.payment.set(updated); },
      error: (e) => this.toast.error(e?.error?.message || 'Failed')
    });
  }
}
