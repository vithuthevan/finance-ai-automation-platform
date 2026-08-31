import { Component, computed } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../core/auth/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatListModule, MatToolbarModule, MatButtonModule, MatIconModule],
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
          <span class="user">{{ auth.session()?.fullName }} · {{ auth.role() }}</span>
          <a mat-button routerLink="/app/profile">Profile</a>
          <button mat-button (click)="auth.logout()">Logout</button>
        </mat-toolbar>
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
export class ShellComponent {
  constructor(readonly auth: AuthService) {}

  readonly title = computed(() => this.auth.hasRole('BUSINESS_OWNER') ? 'Your documents' : 'Firm workspace');

  readonly items = computed(() => {
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
    if (this.auth.hasRole('ADMIN')) {
      items.push(
        { path: '/app/users', label: 'Users' },
        { path: '/app/categories', label: 'Categories' },
        { path: '/app/firm', label: 'Firm settings' }
      );
    }
    if (this.auth.hasRole('ADMIN', 'AUDITOR')) {
      items.push({ path: '/app/audit', label: 'Audit log' });
    }
    return items;
  });
}
