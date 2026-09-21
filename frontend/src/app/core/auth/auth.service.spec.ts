import { describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  it('matches role checks against the active session', () => {
    const http = {
      post: vi.fn(),
      get: vi.fn(() => of(false))
    } as any;
    const router = { navigateByUrl: vi.fn() } as any;
    const service = new AuthService(http, router);

    service.session.set({
      userId: '11111111-1111-1111-1111-111111111111',
      email: 'accountant@example.com',
      fullName: 'Accountant',
      role: 'ACCOUNTANT',
      accessToken: 'token'
    });

    expect(service.hasRole('ACCOUNTANT')).toBe(true);
    expect(service.hasRole('ADMIN')).toBe(false);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('treats upload-only business owners as restricted', () => {
    const http = {
      post: vi.fn(),
      get: vi.fn(() => of(false))
    } as any;
    const router = { navigateByUrl: vi.fn() } as any;
    const service = new AuthService(http, router);

    service.session.set({
      userId: '22222222-2222-2222-2222-222222222222',
      email: 'owner@example.com',
      fullName: 'Owner',
      role: 'BUSINESS_OWNER',
      accessToken: 'token',
      uploadOnly: true
    });

    expect(service.isUploadOnly()).toBe(true);
    expect(service.canWriteLedger()).toBe(false);
  });

  it('allows ledger writes for accountants but not business owners', () => {
    const http = {
      post: vi.fn(),
      get: vi.fn(() => of(false))
    } as any;
    const router = { navigateByUrl: vi.fn() } as any;
    const service = new AuthService(http, router);

    service.session.set({
      userId: '33333333-3333-3333-3333-333333333333',
      email: 'owner-full@example.com',
      fullName: 'Owner',
      role: 'BUSINESS_OWNER',
      accessToken: 'token'
    });
    expect(service.canWriteLedger()).toBe(false);

    service.session.set({
      userId: '44444444-4444-4444-4444-444444444444',
      email: 'accountant@example.com',
      fullName: 'Accountant',
      role: 'ACCOUNTANT',
      accessToken: 'token'
    });
    expect(service.canWriteLedger()).toBe(true);
  });
});
