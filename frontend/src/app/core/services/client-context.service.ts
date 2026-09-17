import { Injectable, computed, signal } from '@angular/core';

const STORAGE_KEY = 'fp:selectedClientId';

export interface ClientOption {
  id: string;
  name: string;
}

/**
 * UI-only workspace client selection (sessionStorage). Does not change API contracts.
 */
@Injectable({ providedIn: 'root' })
export class ClientContextService {
  readonly clients = signal<ClientOption[]>([]);
  readonly clientId = signal('');
  readonly clientName = computed(() => {
    const id = this.clientId();
    return this.clients().find((c) => c.id === id)?.name ?? '';
  });

  setClients(clients: ClientOption[]): void {
    this.clients.set(clients);
    const stored = sessionStorage.getItem(STORAGE_KEY);
    if (stored && clients.some((c) => c.id === stored)) {
      this.clientId.set(stored);
      return;
    }
    if (!this.clientId() && clients[0]) {
      this.select(clients[0].id, false);
    }
  }

  /** Prefer query param, then stored context, then first client. */
  resolveInitial(preferredFromQuery: string | null | undefined, clients: ClientOption[]): string {
    if (preferredFromQuery && clients.some((c) => c.id === preferredFromQuery)) {
      return preferredFromQuery;
    }
    const stored = sessionStorage.getItem(STORAGE_KEY);
    if (stored && clients.some((c) => c.id === stored)) {
      return stored;
    }
    return clients[0]?.id ?? '';
  }

  select(id: string, persist = true): void {
    if (!id) {
      return;
    }
    this.clientId.set(id);
    if (persist) {
      sessionStorage.setItem(STORAGE_KEY, id);
    }
  }
}
