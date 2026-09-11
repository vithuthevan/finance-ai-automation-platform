import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../core/auth/auth.service';
import { ApiService } from '../core/services/api.service';
import { NotificationBellComponent } from '../features/notifications/notification-bell.component';
import { SubscriptionUsage } from '../features/admin/subscription.models';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, MatIconModule, NotificationBellComponent],
  template: `
    <div class="app-frame">
      <aside class="sidebar">
        <div class="brand">
          <span class="brand-mark" aria-hidden="true">◆</span>
          <div>
            <div class="brand-name">Finance Platform</div>
            <div class="brand-tag">Document to Close</div>
          </div>
        </div>

        <nav class="nav">
          @for (item of items(); track item.path) {
            <a class="nav-item" [routerLink]="item.path" routerLinkActive="active">
              <mat-icon>{{ item.icon }}</mat-icon>
              <span>{{ item.label }}</span>
            </a>
          }
        </nav>
      </aside>

      <div class="main">
        <header class="topbar">
          <div class="crumb">
            <span class="crumb-root">Workspace</span>
            <mat-icon class="crumb-sep">chevron_right</mat-icon>
            <span class="crumb-current">{{ title() }}</span>
          </div>
          <div class="topbar-actions">
            @if (!platformMode()) {
              <app-notification-bell />
            }
            <div class="profile">
              <div class="avatar">{{ initials() }}</div>
              <div class="profile-meta">
                <div class="profile-name">{{ auth.session()?.fullName }}</div>
                <div class="profile-role">{{ auth.role() }}</div>
              </div>
            </div>
            @if (!platformMode()) {
              <a mat-button routerLink="/app/profile">Profile</a>
            }
            <button mat-stroked-button class="logout" (click)="auth.logout()">Logout</button>
          </div>
        </header>

        @if (subscriptionBanner()) {
          <div class="subscription-banner warning">{{ subscriptionBanner() }}</div>
        }

        <div class="content">
          <router-outlet />
        </div>
      </div>
    </div>
  `,
  styles: [`
    .app-frame {
      display: grid;
      grid-template-columns: 248px 1fr;
      min-height: 100vh;
      background: var(--fp-canvas);
    }

    .sidebar {
      background: var(--fp-sidebar);
      border-right: 1px solid var(--fp-line);
      padding: 18px 12px 24px;
      position: sticky;
      top: 0;
      height: 100vh;
      overflow: auto;
    }

    .brand {
      display: flex;
      gap: 10px;
      align-items: center;
      padding: 8px 10px 18px;
    }

    .brand-mark {
      width: 34px;
      height: 34px;
      border-radius: 10px;
      display: grid;
      place-items: center;
      background: linear-gradient(145deg, #f29a4a, var(--fp-orange));
      color: #fff;
      font-size: 14px;
      box-shadow: 0 6px 16px rgba(232, 119, 34, 0.28);
    }

    .brand-name {
      font-family: var(--fp-display);
      font-weight: 700;
      font-size: 14px;
      color: var(--fp-ink);
      line-height: 1.2;
    }

    .brand-tag {
      font-size: 11px;
      color: var(--fp-muted);
      margin-top: 2px;
    }

    .nav {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .nav-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 12px;
      border-radius: 10px;
      color: #4b5563;
      text-decoration: none;
      font-size: 13.5px;
      font-weight: 600;
      transition: background .15s ease, color .15s ease;
    }

    .nav-item mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
      color: #7b8494;
    }

    .nav-item:hover {
      background: #eef1f5;
      color: var(--fp-ink);
    }

    .nav-item.active {
      background: var(--fp-orange-soft);
      color: var(--fp-orange-hover);
    }

    .nav-item.active mat-icon {
      color: var(--fp-orange);
    }

    .main {
      min-width: 0;
      display: flex;
      flex-direction: column;
    }

    .topbar {
      height: 64px;
      padding: 0 22px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      background: var(--fp-surface);
      border-bottom: 1px solid var(--fp-line);
      position: sticky;
      top: 0;
      z-index: 5;
    }

    .crumb {
      display: flex;
      align-items: center;
      gap: 2px;
      min-width: 0;
    }

    .crumb-root {
      color: var(--fp-muted);
      font-size: 13px;
      font-weight: 500;
    }

    .crumb-sep {
      font-size: 18px;
      width: 18px;
      height: 18px;
      color: #c0c6d0;
    }

    .crumb-current {
      font-family: var(--fp-display);
      font-size: 14px;
      font-weight: 600;
      color: var(--fp-ink);
    }

    .topbar-actions {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .profile {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 4px 8px 4px 4px;
      margin-right: 4px;
    }

    .avatar {
      width: 34px;
      height: 34px;
      border-radius: 50%;
      display: grid;
      place-items: center;
      background: var(--fp-orange-soft);
      color: var(--fp-orange-hover);
      font-size: 12px;
      font-weight: 700;
    }

    .profile-name {
      font-size: 13px;
      font-weight: 600;
      line-height: 1.2;
    }

    .profile-role {
      font-size: 11px;
      color: var(--fp-muted);
      text-transform: capitalize;
    }

    .logout {
      border-color: var(--fp-line) !important;
    }

    .content {
      flex: 1;
    }

    @media (max-width: 900px) {
      .app-frame {
        grid-template-columns: 1fr;
      }

      .sidebar {
        position: relative;
        height: auto;
        border-right: 0;
        border-bottom: 1px solid var(--fp-line);
      }

      .profile-meta {
        display: none;
      }
    }
  `]
})
export class ShellComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly platformMode = signal(this.router.url.startsWith('/platform'));
  readonly subscriptionBanner = signal<string | null>(null);

  constructor() {
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
      this.platformMode.set(this.router.url.startsWith('/platform'));
    });
  }

  ngOnInit(): void {
    if (this.auth.hasRole('ADMIN') && !this.platformMode()) {
      this.api.get<SubscriptionUsage>('/api/v1/subscription/usage').subscribe({
        next: (usage) => {
          if (!usage.writeAllowed) {
            this.subscriptionBanner.set('Your subscription is suspended or inactive. Existing data remains available in read-only mode.');
            return;
          }
          const warnings: string[] = [];
          if (usage.documents.percentUsed >= 80) warnings.push(`Documents ${usage.documents.percentUsed}%`);
          if (usage.aiProcessing.percentUsed >= 80) warnings.push(`AI ${usage.aiProcessing.percentUsed}%`);
          if (usage.storageBytes.percentUsed >= 80) warnings.push(`Storage ${usage.storageBytes.percentUsed}%`);
          this.subscriptionBanner.set(warnings.length ? `Plan usage warning: ${warnings.join(' · ')}` : null);
        },
        error: () => this.subscriptionBanner.set(null)
      });
    }
  }

  readonly title = computed(() => {
    if (this.platformMode()) {
      return 'Platform administration';
    }
    return this.auth.hasRole('BUSINESS_OWNER') ? 'Your documents' : 'Firm workspace';
  });

  readonly initials = computed(() => {
    const name = this.auth.session()?.fullName?.trim() || 'U';
    const parts = name.split(/\s+/).filter(Boolean);
    return ((parts[0]?.[0] || 'U') + (parts[1]?.[0] || '')).toUpperCase();
  });

  readonly items = computed((): NavItem[] => {
    if (this.platformMode()) {
      return [{ path: '/platform', label: 'Firms', icon: 'apartment' }];
    }
    if (this.auth.isUploadOnly()) {
      return [
        { path: '/app/owner', label: 'Requested documents', icon: 'assignment' },
        { path: '/app/documents', label: 'My documents', icon: 'folder' }
      ];
    }
    if (this.auth.hasRole('BUSINESS_OWNER')) {
      return [
        { path: '/app/owner', label: 'Home', icon: 'home' },
        { path: '/app/documents', label: 'My documents', icon: 'folder' },
        { path: '/app/expenses', label: 'Expenses', icon: 'payments' },
        { path: '/app/income', label: 'Income', icon: 'trending_up' },
        { path: '/app/reports', label: 'Summary', icon: 'insights' }
      ];
    }
    const items: NavItem[] = [
      { path: '/app/dashboard', label: 'Dashboard', icon: 'dashboard' },
      { path: '/app/clients', label: 'Clients', icon: 'groups' },
      { path: '/app/documents', label: 'Documents', icon: 'description' },
      { path: '/app/expenses', label: 'Expenses', icon: 'payments' },
      { path: '/app/income', label: 'Income', icon: 'trending_up' },
      { path: '/app/banking', label: 'Banking', icon: 'account_balance' },
      { path: '/app/close', label: 'Close', icon: 'task_alt' },
      { path: '/app/reports', label: 'Reports', icon: 'insights' }
    ];
    if (this.auth.hasRole('ADMIN', 'ACCOUNTANT')) {
      items.splice(1, 0, { path: '/app/work', label: 'My work', icon: 'checklist' });
    }
    if (this.auth.hasRole('ADMIN')) {
      items.push(
        { path: '/app/users', label: 'Users', icon: 'manage_accounts' },
        { path: '/app/categories', label: 'Categories', icon: 'category' },
        { path: '/app/firm', label: 'Firm settings', icon: 'settings' },
        { path: '/app/subscription', label: 'Subscription', icon: 'workspace_premium' }
      );
    }
    if (this.auth.hasRole('ADMIN', 'AUDITOR')) {
      items.push({ path: '/app/audit', label: 'Audit log', icon: 'policy' });
    }
    if (this.auth.isPlatformAdmin()) {
      items.push({ path: '/platform', label: 'Platform admin', icon: 'admin_panel_settings' });
    }
    return items;
  });
}
