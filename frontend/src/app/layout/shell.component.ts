import { Component, OnInit, computed, inject, signal } from '@angular/core';

import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { filter } from 'rxjs';

import { MatSidenavModule } from '@angular/material/sidenav';

import { MatListModule } from '@angular/material/list';

import { MatToolbarModule } from '@angular/material/toolbar';

import { MatButtonModule } from '@angular/material/button';

import { MatIconModule } from '@angular/material/icon';

import { AuthService } from '../core/auth/auth.service';

import { ApiService } from '../core/services/api.service';

import { NotificationBellComponent } from '../features/notifications/notification-bell.component';

import { SubscriptionUsage } from '../features/admin/subscription.models';



@Component({

  selector: 'app-shell',

  standalone: true,

  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatListModule, MatToolbarModule, MatButtonModule, MatIconModule, NotificationBellComponent],

  template: `

    <mat-sidenav-container class="shell">

      <mat-sidenav mode="side" opened class="nav">

        <div class="brand">Finance Platform</div>

        <mat-nav-list>

          @for (item of items(); track item.path) {

            <a mat-list-item [routerLink]="item.path" routerLinkActive="active">{{ item.label }}</a>

          }

        </mat-nav-list>

      </mat-sidenav>

      <mat-sidenav-content>

        <mat-toolbar color="primary">

          <span>{{ title() }}</span>

          <span class="spacer"></span>

          @if (!platformMode()) {

            <app-notification-bell />

          }

          <span class="user">{{ auth.session()?.fullName }} · {{ auth.role() }}</span>

          @if (!platformMode()) {

            <a mat-button routerLink="/app/profile">Profile</a>

          }

          <button mat-button (click)="auth.logout()">Logout</button>

        </mat-toolbar>

        @if (subscriptionBanner()) {

          <div class="subscription-banner warning">{{ subscriptionBanner() }}</div>

        }

        <router-outlet />

      </mat-sidenav-content>

    </mat-sidenav-container>

  `,

  styles: [`

    .shell { height: 100vh; }

    .nav { width: 240px; }

    .brand { padding: 20px 16px 8px; font-weight: 600; }

    .spacer { flex: 1; }

    .user { margin-right: 12px; font-size: 13px; }

    a.active { background: rgba(0,0,0,.06); }

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



  readonly items = computed(() => {

    if (this.platformMode()) {

      return [{ path: '/platform', label: 'Firms' }];

    }

    if (this.auth.isUploadOnly()) {

      return [

        { path: '/app/owner', label: 'Requested documents' },

        { path: '/app/documents', label: 'My documents' }

      ];

    }

    if (this.auth.hasRole('BUSINESS_OWNER')) {

      return [

        { path: '/app/owner', label: 'Home' },

        { path: '/app/documents', label: 'My documents' },

        { path: '/app/expenses', label: 'Expenses' },

        { path: '/app/income', label: 'Income' },

        { path: '/app/reports', label: 'Summary' }

      ];

    }

    const items = [

      { path: '/app/dashboard', label: 'Dashboard' },

      { path: '/app/clients', label: 'Clients' },

      { path: '/app/documents', label: 'Documents' },

      { path: '/app/expenses', label: 'Expenses' },

      { path: '/app/income', label: 'Income' },

      { path: '/app/banking', label: 'Banking' },

      { path: '/app/close', label: 'Close' },

      { path: '/app/reports', label: 'Reports' }

    ];

    if (this.auth.hasRole('ADMIN', 'ACCOUNTANT')) {

      items.splice(1, 0, { path: '/app/work', label: 'My work' });

    }

    if (this.auth.hasRole('ADMIN')) {

      items.push(

        { path: '/app/users', label: 'Users' },

        { path: '/app/categories', label: 'Categories' },

        { path: '/app/firm', label: 'Firm settings' },

        { path: '/app/subscription', label: 'Subscription' }

      );

    }

    if (this.auth.hasRole('ADMIN', 'AUDITOR')) {

      items.push({ path: '/app/audit', label: 'Audit log' });

    }

    if (this.auth.isPlatformAdmin()) {

      items.push({ path: '/platform', label: 'Platform admin' });

    }

    return items;

  });

}

