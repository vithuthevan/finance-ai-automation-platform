export interface ArCustomer {
  id: string;
  name: string;
  email?: string;
  clientId?: string;
  paymentTermsDays: number;
  active: boolean;
}

export interface SalesInvoiceLine {
  description: string;
  quantity: number;
  unitPrice: number;
  taxRatePercent?: number;
}

export interface SalesInvoice {
  id: string;
  customerId: string;
  customerName: string;
  invoiceNumber: string;
  documentStatus: string;
  settlementStatus: string;
  issueDate?: string;
  dueDate?: string;
  currency: string;
  subtotal: number;
  taxTotal: number;
  total: number;
  allocatedTotal: number;
  outstanding: number;
  notes?: string;
  lines: Array<{
    id: string;
    lineNo: number;
    description: string;
    quantity: number;
    unitPrice: number;
    lineTotal: number;
    taxRatePercent: number;
  }>;
}

export interface ArSummary {
  totalOutstanding: number;
  overdue: number;
  dueThisWeek: number;
  collectedThisMonth: number;
  ageing: {
    current: number;
    days1To30: number;
    days31To60: number;
    days61To90: number;
    days90Plus: number;
  };
}

export interface PaymentAllocation {
  id: string;
  invoiceId: string;
  invoiceNumber?: string;
  amount: number;
  active: boolean;
}

export interface ArPayment {
  id: string;
  customerId?: string;
  paymentDate: string;
  amount: number;
  unallocatedAmount: number;
  reference?: string;
  status: string;
  source?: string;
  allocations: PaymentAllocation[];
}
