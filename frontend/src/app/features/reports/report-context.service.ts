import { Injectable, signal } from '@angular/core';

import { finalize } from 'rxjs';

import { ApiService } from '../../core/services/api.service';

import { AuthService } from '../../core/auth/auth.service';

import { ToastService } from '../../shared/toast.service';

import { monthStart as sharedMonthStart, today as sharedToday } from '../../shared/date.util';



export { money } from '../../shared/money';



@Injectable({ providedIn: 'root' })

export class ReportContextService {

  readonly clients = signal<any[]>([]);

  readonly clientId = signal('');

  readonly from = signal(monthStart());

  readonly to = signal(today());

  readonly compareFrom = signal('');

  readonly compareTo = signal('');

  readonly lockClient = signal(false);

  readonly clientsLoading = signal(false);

  readonly clientsError = signal('');



  constructor(

    private api: ApiService,

    private auth: AuthService,

    private toast: ToastService

  ) {}



  loadClients(onReady?: () => void): void {

    this.clientsLoading.set(true);

    this.clientsError.set('');

    this.api.list<any>('/api/v1/clients').pipe(

      finalize(() => this.clientsLoading.set(false))

    ).subscribe({

      next: (clients) => {

        this.clients.set(clients);

        this.lockClient.set(this.auth.hasRole('BUSINESS_OWNER') && clients.length <= 1);

        if (clients.length === 0) {

          this.clientsError.set('No clients are available for reporting.');

          return;

        }

        if (!this.clientId() && clients[0]) {

          this.clientId.set(clients[0].id);

        }

        onReady?.();

      },

      error: () => {

        this.clients.set([]);

        this.clientsError.set('Unable to load clients. Please refresh the page or try again.');

        this.toast.error('Unable to load clients for reporting.');

      }

    });

  }



  params() {

    return { from: this.from(), to: this.to() };

  }

}



export function monthStart(): string {

  return sharedMonthStart();

}



export function today(): string {

  return sharedToday();

}


