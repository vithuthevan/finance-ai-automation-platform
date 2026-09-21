import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth/auth.service';
import { ApiService } from '../core/services/api.service';
import { ClientContextService } from '../core/services/client-context.service';
import { NotificationBellComponent } from '../features/notifications/notification-bell.component';
import { SubscriptionUsage } from '../features/admin/subscription.models';
import { ClientMonthEndBannerComponent } from '../shared/client-month-end-banner.component';

interface NavItem {
  path: string;
  labelKey: string;
  icon: string;
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatSelectModule, NotificationBellComponent, TranslatePipe,
    ClientMonthEndBannerComponent
  ],
  template: `
    <div class="app-frame" [class.nav-open]="navOpen()">
      @if (navOpen()) {
        <button type="button" class="nav-overlay" aria-label="Close navigation" (click)="closeNav()"></button>
      }

      <aside class="sidebar" [class.is-open]="navOpen()" aria-label="Main navigation">
        <div class="brand">
          <span class="brand-mark" aria-hidden="true">
            <mat-icon>account_balance</mat-icon>
          </span>
          <div class="brand-text">
            <div class="brand-name">{{ 'appName' | translate }}</div>
            <div class="brand-tag">Document to Close</div>
          </div>
        </div>

        <nav class="nav-groups">
          @for (group of navGroups(); track group.title) {
            <div class="nav-group">
              <div class="nav-group-title">{{ group.title }}</div>
              @for (item of group.items; track item.path) {
                <a class="nav-item" [routerLink]="item.path" routerLinkActive="active" (click)="closeNav()">
                  <mat-icon aria-hidden="true">{{ item.icon }}</mat-icon>
                  <span>{{ item.labelKey | translate }}</span>
                </a>
              }
            </div>
          }
        </nav>
      </aside>

      <div class="main">
        <header class="topbar">
          <div class="topbar-start">
            <button type="button" class="nav-toggle" aria-label="Open navigation" (click)="toggleNav()">
              <mat-icon>menu</mat-icon>
            </button>
            <div class="crumb">
              <span class="crumb-root">{{ workspaceLabel() }}</span>
              <mat-icon class="crumb-sep" aria-hidden="true">chevron_right</mat-icon>
              <span class="crumb-current">{{ title() }}</span>
            </div>
          </div>

          <div class="topbar-actions">
            @if (showClientContext()) {
              <div class="client-context">
                <mat-icon aria-hidden="true">business</mat-icon>
                @if (clientContext.clients().length <= 1) {
                  <span class="client-context__name">{{ clientContext.clientName() || 'Client' }}</span>
                } @else {
                  <mat-form-field appearance="outline" class="client-context__field" subscriptSizing="dynamic">
                    <mat-label>Active client</mat-label>
                    <mat-select
                      [value]="clientContext.clientId()"
                      (selectionChange)="onClientSelected($event.value)"
                      aria-label="Select active client">
                      @for (c of clientContext.clients(); track c.id) {
                        <mat-option [value]="c.id">{{ c.name }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>
                }
              </div>
            }
            @if (!platformMode()) {
              <app-notification-bell />
            }
            <div class="profile">
              <div class="avatar" aria-hidden="true">{{ initials() }}</div>
              <div class="profile-meta">
                <div class="profile-name">{{ auth.session()?.fullName }}</div>
                <div class="profile-role">{{ roleLabel() }}</div>
              </div>
            </div>
            @if (!platformMode()) {
              <a mat-button routerLink="/app/profile">Profile</a>
            }
            <button mat-stroked-button class="logout" type="button" (click)="auth.logout()">Logout</button>
          </div>
        </header>

        @if (subscriptionBanner()) {
          <div class="subscription-banner warning" role="status">{{ subscriptionBanner() }}</div>
        }

        <div class="content">
          @if (showClientMonthEndBanner()) {
            <app-client-month-end-banner />
          }
          <router-outlet />
        </div>
      </div>
    </div>
  `,
  styles: [`
    .app-frame {
      display: grid;
      grid-template-columns: var(--fp-sidebar-width) 1fr;
      min-height: 100vh;
      background: var(--fp-background);
    }

    .nav-overlay {
      display: none;
      position: fixed;
      inset: 0;
      z-index: var(--fp-z-overlay);
      border: 0;
      background: rgba(26, 35, 50, 0.45);
      cursor: pointer;
    }

    .sidebar {
      background: var(--fp-surface);
      border-right: 1px solid var(--fp-border);
      padding: var(--fp-space-4) var(--fp-space-3) var(--fp-space-6);
      position: sticky;
      top: 0;
      height: 100vh;
      overflow: auto;
      z-index: var(--fp-z-sidebar);
    }

    .brand {
      display: flex;
      gap: var(--fp-space-3);
      align-items: center;
      padding: var(--fp-space-2) var(--fp-space-2) var(--fp-space-4);
      margin-bottom: var(--fp-space-2);
      border-bottom: 1px solid var(--fp-border);
    }

    .brand-mark {
      width: 36px;
      height: 36px;
      border-radius: var(--fp-radius-md);
      display: grid;
      place-items: center;
      background: var(--fp-primary);
      color: var(--fp-primary-on);
    }

    .brand-mark mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }

    .brand-name {
      font-family: var(--fp-font-display);
      font-weight: var(--fp-weight-bold);
      font-size: 13px;
      color: var(--fp-text-primary);
      line-height: 1.2;
    }

    .brand-tag {
      font-size: 11px;
      color: var(--fp-text-muted);
      margin-top: 2px;
    }

    .nav-group {
      margin-bottom: var(--fp-space-4);
    }

    .nav-group-title {
      font-size: 10px;
      font-weight: var(--fp-weight-semibold);
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: var(--fp-text-muted);
      padding: var(--fp-space-2) var(--fp-space-3);
    }

    .nav-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 9px 12px;
      border-radius: var(--fp-radius-md);
      color: var(--fp-text-secondary);
      text-decoration: none;
      font-size: 13px;
      font-weight: var(--fp-weight-semibold);
      transition: background var(--fp-transition-fast), color var(--fp-transition-fast);
    }

    .nav-item mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
      color: var(--fp-text-muted);
    }

    .nav-item:hover {
      background: var(--fp-surface-hover);
      color: var(--fp-text-primary);
    }

    .nav-item.active {
      background: var(--fp-primary-subtle);
      color: var(--fp-primary-hover);
    }

    .nav-item.active mat-icon {
      color: var(--fp-primary);
    }

    .main {
      min-width: 0;
      display: flex;
      flex-direction: column;
    }

    .topbar {
      min-height: var(--fp-topbar-height);
      padding: 0 var(--fp-space-5);
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--fp-space-4);
      background: var(--fp-surface);
      border-bottom: 1px solid var(--fp-border);
      position: sticky;
      top: 0;
      z-index: var(--fp-z-topbar);
      min-width: 0;
    }

    .topbar-start {
      display: flex;
      align-items: center;
      gap: var(--fp-space-2);
      min-width: 0;
      flex: 1;
    }

    .nav-toggle {
      display: none;
      border: 0;
      background: transparent;
      padding: var(--fp-space-2);
      border-radius: var(--fp-radius-md);
      cursor: pointer;
      color: var(--fp-text-secondary);
    }

    .nav-toggle:hover {
      background: var(--fp-surface-hover);
    }

    .nav-toggle:focus-visible {
      outline: 2px solid var(--fp-primary);
      outline-offset: 2px;
    }

    .crumb {
      display: flex;
      align-items: center;
      gap: 2px;
      min-width: 0;
    }

    .crumb-root {
      color: var(--fp-text-muted);
      font-size: 13px;
      font-weight: var(--fp-weight-medium);
    }

    .crumb-sep {
      font-size: 18px;
      width: 18px;
      height: 18px;
      color: var(--fp-border-strong);
    }

    .crumb-current {
      font-family: var(--fp-font-display);
      font-size: 14px;
      font-weight: var(--fp-weight-semibold);
      color: var(--fp-text-primary);
    }

    .topbar-actions {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: var(--fp-space-2);
      flex-shrink: 0;
    }

    .client-context {
      display: flex;
      align-items: center;
      gap: var(--fp-space-2);
      padding: 0 var(--fp-space-2);
      border-right: 1px solid var(--fp-border);
      margin-right: var(--fp-space-1);
      max-width: 240px;
    }

    .client-context mat-icon {
      color: var(--fp-primary);
      font-size: 20px;
      width: 20px;
      height: 20px;
    }

    .client-context__name {
      font-size: 13px;
      font-weight: var(--fp-weight-semibold);
      color: var(--fp-text-primary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 180px;
    }

    .client-context__field {
      width: 168px;
      margin: 0;
      --mat-form-field-container-vertical-padding: 6px;
    }

    .profile {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 4px 8px 4px 4px;
    }

    .avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      display: grid;
      place-items: center;
      background: var(--fp-primary-subtle);
      color: var(--fp-primary-hover);
      font-size: 11px;
      font-weight: var(--fp-weight-bold);
    }

    .profile-name {
      font-size: 13px;
      font-weight: var(--fp-weight-semibold);
      line-height: 1.2;
    }

    .profile-role {
      font-size: 11px;
      color: var(--fp-text-muted);
    }

    .logout {
      border-color: var(--fp-border) !important;
    }

    .content {
      flex: 1;
    }

    @media (max-width: 1023px) {
      .app-frame {
        grid-template-columns: 1fr;
      }

      .nav-toggle {
        display: inline-flex;
      }

      .nav-overlay {
        display: block;
      }

      .sidebar {
        position: fixed;
        left: 0;
        top: 0;
        width: min(var(--fp-sidebar-width), 88vw);
        transform: translateX(-100%);
        transition: transform var(--fp-transition-base);
        box-shadow: var(--fp-shadow-md);
      }

      .sidebar.is-open {
        transform: translateX(0);
      }

      .client-context {
        border-right: 0;
        max-width: 100%;
        flex: 1 1 100%;
        order: -1;
      }

      .topbar {
        flex-wrap: wrap;
        padding: var(--fp-space-3) var(--fp-space-4);
      }

      .topbar-actions {
        width: 100%;
      }

      .profile-meta {
        display: none;
      }
    }

    @media (min-width: 1024px) {
      .nav-overlay {
        display: none !important;
      }
    }
  `]
})
export class ShellComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  readonly clientContext = inject(ClientContextService);

  readonly platformMode = signal(this.router.url.startsWith('/platform'));
  readonly subscriptionBanner = signal<string | null>(null);
  readonly navOpen = signal(false);

  constructor() {
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
      this.platformMode.set(this.router.url.startsWith('/platform'));
      this.closeNav();
    });
  }

  ngOnInit(): void {
    if (!this.platformMode() && !this.auth.isUploadOnly()) {
      this.api.list<{ id: string; name: string }>('/api/v1/clients').subscribe({
        next: (clients) => this.clientContext.setClients(clients.map((c) => ({ id: c.id, name: c.name }))),
        error: () => {}
      });
    }
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

  toggleNav(): void {
    this.navOpen.update((v) => !v);
  }

  closeNav(): void {
    this.navOpen.set(false);
  }

  onClientSelected(id: string): void {
    this.clientContext.select(id);
  }

  readonly title = computed(() => {
    if (this.platformMode()) {
      return 'Platform administration';
    }
    return this.auth.hasRole('BUSINESS_OWNER') ? 'Your documents' : 'Firm workspace';
  });

  readonly workspaceLabel = computed(() => {
    if (this.platformMode()) {
      return 'Platform';
    }
    return this.auth.hasRole('BUSINESS_OWNER') ? 'Client portal' : 'Practice workspace';
  });

  readonly initials = computed(() => {
    const name = this.auth.session()?.fullName?.trim() || 'U';
    const parts = name.split(/\s+/).filter(Boolean);
    return ((parts[0]?.[0] || 'U') + (parts[1]?.[0] || '')).toUpperCase();
  });

  roleLabel(): string {
    const role = this.auth.role();
    if (!role) {
      return '';
    }
    return role.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
  }

  showClientContext(): boolean {
    if (this.platformMode() || this.auth.isUploadOnly()) {
      return false;
    }
    return !this.auth.hasRole('BUSINESS_OWNER') || this.clientContext.clients().length > 0;
  }

  showClientMonthEndBanner(): boolean {
    return this.showClientContext()
      && this.auth.hasRole('ADMIN', 'ACCOUNTANT')
      && !this.platformMode()
      && !!this.clientContext.clientId();
  }

  private flatItems(): NavItem[] {
    if (this.platformMode()) {
      return [{ path: '/platform', labelKey: 'nav.firms', icon: 'apartment' }];
    }
    if (this.auth.isUploadOnly()) {
      return [
        { path: '/app/owner', labelKey: 'nav.requestedDocuments', icon: 'assignment' },
        { path: '/app/documents', labelKey: 'nav.myDocuments', icon: 'folder' }
      ];
    }
    if (this.auth.hasRole('BUSINESS_OWNER')) {
      return [
        { path: '/app/owner', labelKey: 'nav.home', icon: 'home' },
        { path: '/app/documents', labelKey: 'nav.myDocuments', icon: 'folder' },
        { path: '/app/reports', labelKey: 'nav.summary', icon: 'insights' }
      ];
    }
    const items: NavItem[] = [
      { path: '/app/dashboard', labelKey: 'nav.dashboard', icon: 'dashboard' },
      { path: '/app/clients', labelKey: 'nav.clients', icon: 'groups' },
      { path: '/app/documents', labelKey: 'nav.documents', icon: 'description' },
      { path: '/app/expenses', labelKey: 'nav.expenses', icon: 'payments' },
      { path: '/app/income', labelKey: 'nav.income', icon: 'trending_up' },
      { path: '/app/ar', labelKey: 'nav.ar', icon: 'receipt_long' },
      { path: '/app/banking', labelKey: 'nav.banking', icon: 'account_balance' },
      { path: '/app/close', labelKey: 'nav.close', icon: 'task_alt' },
      { path: '/app/reports', labelKey: 'nav.reports', icon: 'insights' }
    ];
    if (this.auth.hasRole('ADMIN', 'ACCOUNTANT')) {
      items.splice(1, 0, { path: '/app/month-end', labelKey: 'nav.monthEnd', icon: 'calendar_month' });
      items.splice(2, 0, { path: '/app/work', labelKey: 'nav.work', icon: 'checklist' });
      items.splice(3, 0, { path: '/app/requests', labelKey: 'nav.requests', icon: 'assignment' });
      items.splice(4, 0, { path: '/app/client-chase', labelKey: 'nav.clientChase', icon: 'notifications_active' });
    }
    if (this.auth.hasRole('ADMIN')) {
      items.push(
        { path: '/app/users', labelKey: 'nav.users', icon: 'manage_accounts' },
        { path: '/app/categories', labelKey: 'nav.categories', icon: 'category' },
        { path: '/app/firm', labelKey: 'nav.firm', icon: 'settings' },
        { path: '/app/subscription', labelKey: 'nav.subscription', icon: 'workspace_premium' }
      );
    }
    if (this.auth.hasRole('ADMIN', 'AUDITOR')) {
      items.push({ path: '/app/audit', labelKey: 'nav.audit', icon: 'policy' });
    }
    if (this.auth.isPlatformAdmin()) {
      items.push({ path: '/platform', labelKey: 'nav.platform', icon: 'admin_panel_settings' });
    }
    return items;
  }

  readonly navGroups = computed((): NavGroup[] => {
    const items = this.flatItems();
    if (this.platformMode() || this.auth.isUploadOnly() || this.auth.hasRole('BUSINESS_OWNER')) {
      return [{ title: 'Menu', items }];
    }
    const byPath = new Map(items.map((i) => [i.path, i]));
    const pick = (...paths: string[]) => paths.map((p) => byPath.get(p)).filter(Boolean) as NavItem[];
    const groups: NavGroup[] = [
      { title: 'Overview', items: pick('/app/dashboard', '/app/month-end', '/app/work', '/app/requests') },
      { title: 'Clients', items: pick('/app/clients') },
      {
        title: 'Operations',
        items: pick('/app/documents', '/app/expenses', '/app/income')
      },
      { title: 'Banking', items: pick('/app/banking') },
      { title: 'Period close', items: pick('/app/close') },
      { title: 'Reporting', items: pick('/app/reports') },
      {
        title: 'Administration',
        items: pick(
          '/app/users',
          '/app/categories',
          '/app/firm',
          '/app/subscription',
          '/app/audit',
          '/platform'
        )
      }
    ];
    return groups.filter((g) => g.items.length > 0);
  });
}
