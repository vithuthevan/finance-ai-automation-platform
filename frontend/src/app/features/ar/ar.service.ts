import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { ArPayment, SalesInvoice } from './ar.models';

@Injectable({ providedIn: 'root' })
export class ArService {
  private readonly api = inject(ApiService);

  getPayment(id: string): Observable<ArPayment> {
    return this.api.get<ArPayment>(`/api/v1/ar/payments/${id}`);
  }

  allocate(paymentId: string, allocations: Array<{ invoiceId: string; amount: number }>): Observable<ArPayment> {
    return this.api.post<ArPayment>(`/api/v1/ar/payments/${paymentId}/allocate`, { allocations });
  }

  reverseAllocation(paymentId: string, allocationId: string, reason?: string): Observable<ArPayment> {
    return this.api.post<ArPayment>(
      `/api/v1/ar/payments/${paymentId}/allocations/${allocationId}/reverse`,
      { reason: reason || null }
    );
  }

  reversePayment(paymentId: string, reason: string): Observable<ArPayment> {
    return this.api.post<ArPayment>(`/api/v1/ar/payments/${paymentId}/reverse`, { reason });
  }

  listInvoices(params: Record<string, string | number | undefined> = {}): Observable<{ content: SalesInvoice[] }> {
    return this.api.get<{ content: SalesInvoice[] }>('/api/v1/ar/invoices', params);
  }

  downloadPdf(invoiceId: string): Observable<Blob> {
    return this.api.download(`/api/v1/ar/invoices/${invoiceId}/pdf`);
  }
}
