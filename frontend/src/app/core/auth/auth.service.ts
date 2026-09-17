import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, tap, throwError } from 'rxjs';

export interface SessionUser {
  userId: string;
  email: string;
  fullName: string;
  role: string;
  accessToken: string;
  uploadOnly?: boolean;
}

interface AuthApiResponse {
  userId: string;
  email: string;
  fullName: string;
  role: string;
  accessToken: string;
  uploadOnly?: boolean;
  refreshToken?: string | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private refreshInFlight: Observable<SessionUser> | null = null;
  private bootstrapped = false;

  readonly session = signal<SessionUser | null>(null);
  readonly role = computed(() => this.session()?.role ?? null);
  readonly isAuthenticated = computed(() => !!this.session()?.accessToken);
  readonly uploadOnly = computed(() => this.hasRole('BUSINESS_OWNER') && !!this.session()?.uploadOnly);
  readonly platformAdmin = signal(false);

  constructor(private http: HttpClient, private router: Router) {}

  /**
   * Restore access token from httpOnly refresh cookie (no localStorage secrets).
   */
  bootstrapSession(): Observable<boolean> {
    if (this.bootstrapped && this.isAuthenticated()) {
      return of(true);
    }
    this.bootstrapped = true;
    return this.http.post<AuthApiResponse>('/api/v1/auth/refresh', {}, { withCredentials: true }).pipe(
      tap((response) => this.persist(toSession(response))),
      map(() => true),
      catchError(() => {
        this.session.set(null);
        this.platformAdmin.set(false);
        return of(false);
      })
    );
  }

  login(email: string, password: string) {
    return this.http.post<AuthApiResponse>('/api/v1/auth/login', { email, password }, { withCredentials: true }).pipe(
      tap((response) => this.persist(toSession(response)))
    );
  }

  register(payload: { firmName: string; email: string; password: string; fullName: string }) {
    return this.http.post('/api/v1/auth/register', payload, { withCredentials: true });
  }

  /**
   * Rotates the refresh cookie and issues a new access token in memory.
   */
  refreshSession(): Observable<SessionUser> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }
    this.refreshInFlight = this.http.post<AuthApiResponse>('/api/v1/auth/refresh', {}, { withCredentials: true }).pipe(
      map((response) => toSession(response)),
      tap((session) => this.persist(session)),
      shareReplay(1),
      finalize(() => {
        this.refreshInFlight = null;
      })
    );
    return this.refreshInFlight;
  }

  logout(): void {
    this.http.post('/api/v1/auth/logout', {}, { withCredentials: true }).subscribe({
      next: () => this.finishLocalLogout(),
      error: () => this.finishLocalLogout()
    });
  }

  clearSessionAndRedirect(): void {
    this.http.post('/api/v1/auth/logout', {}, { withCredentials: true }).subscribe({
      next: () => this.finishLocalLogout(),
      error: () => this.finishLocalLogout()
    });
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
    this.session.set(session);
    this.refreshPlatformAccess();
  }

  refreshPlatformAccess(): void {
    if (!this.isAuthenticated()) {
      this.platformAdmin.set(false);
      return;
    }
    this.http.get<{ platformAdmin: boolean }>('/api/v1/platform/me', { withCredentials: true }).subscribe({
      next: (response) => this.platformAdmin.set(!!response.platformAdmin),
      error: () => this.platformAdmin.set(false)
    });
  }

  isPlatformAdmin(): boolean {
    return this.platformAdmin();
  }

  private finishLocalLogout(): void {
    this.session.set(null);
    this.platformAdmin.set(false);
    this.router.navigateByUrl('/login');
  }
}

function toSession(response: AuthApiResponse): SessionUser {
  return {
    userId: response.userId,
    email: response.email,
    fullName: response.fullName,
    role: response.role,
    accessToken: response.accessToken,
    uploadOnly: response.uploadOnly
  };
}
