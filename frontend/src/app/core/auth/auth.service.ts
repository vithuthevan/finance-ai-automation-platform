import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';

export interface SessionUser {
  userId: string;
  email: string;
  fullName: string;
  role: string;
  accessToken: string;
  refreshToken?: string;
  uploadOnly?: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly storageKey = 'fp.session';
  readonly session = signal<SessionUser | null>(this.readSession());
  readonly role = computed(() => this.session()?.role ?? null);
  readonly isAuthenticated = computed(() => !!this.session()?.accessToken);
  readonly uploadOnly = computed(() => this.hasRole('BUSINESS_OWNER') && !!this.session()?.uploadOnly);
  readonly platformAdmin = signal(false);

  constructor(private http: HttpClient, private router: Router) {
    if (this.isAuthenticated()) {
      this.refreshPlatformAccess();
    }
  }

  login(email: string, password: string) {
    return this.http.post<SessionUser>('/api/v1/auth/login', { email, password }).pipe(
      tap((session) => this.persist(session))
    );
  }

  register(payload: { firmName: string; email: string; password: string; fullName: string }) {
    return this.http.post('/api/v1/auth/register', payload);
  }

  logout(): void {
    const refreshToken = this.session()?.refreshToken;
    if (refreshToken) {
      this.http.post('/api/v1/auth/logout', { refreshToken }).subscribe({ error: () => undefined });
    }
    localStorage.removeItem(this.storageKey);
    this.session.set(null);
    this.router.navigateByUrl('/login');
  }

  hasRole(...roles: string[]): boolean {
    const role = this.role();
    return !!role && roles.includes(role);
  }

  isUploadOnly(): boolean {
    return this.uploadOnly();
  }

  canMutateDocuments(): boolean {
    return this.hasRole('ADMIN', 'ACCOUNTANT');
  }

  canUploadDocuments(): boolean {
    return this.hasRole('ADMIN', 'ACCOUNTANT', 'BUSINESS_OWNER') && !this.hasRole('AUDITOR');
  }

  homePath(): string {
    if (this.isUploadOnly()) {
      return '/app/owner';
    }
    if (this.hasRole('BUSINESS_OWNER')) {
      return '/app/owner';
    }
    return '/app/dashboard';
  }

  persist(session: SessionUser): void {
    localStorage.setItem(this.storageKey, JSON.stringify(session));
    this.session.set(session);
    this.refreshPlatformAccess();
  }

  refreshPlatformAccess(): void {
    if (!this.isAuthenticated()) {
      this.platformAdmin.set(false);
      return;
    }
    this.http.get<{ platformAdmin: boolean }>('/api/v1/platform/me').subscribe({
      next: (response) => this.platformAdmin.set(!!response.platformAdmin),
      error: () => this.platformAdmin.set(false)
    });
  }

  isPlatformAdmin(): boolean {
    return this.platformAdmin();
  }

  private readSession(): SessionUser | null {
    const raw = localStorage.getItem(this.storageKey);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as SessionUser;
    } catch {
      return null;
    }
  }
}
