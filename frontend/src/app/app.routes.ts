import { Routes } from '@angular/router';
import { authGuard, guestGuard, ledgerGuard, ownerGuard, roleGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'app/dashboard' },
  { path: 'login', canActivate: [guestGuard], loadComponent: () => import('./features/auth/login.page').then(m => m.LoginPage) },
  { path: 'register', canActivate: [guestGuard], loadComponent: () => import('./features/auth/register.page').then(m => m.RegisterPage) },
  { path: 'unauthorized', loadComponent: () => import('./features/auth/unauthorized.page').then(m => m.UnauthorizedPage) },
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then(m => m.ShellComponent),
    children: [
      { path: 'dashboard', canActivate: [ledgerGuard], loadComponent: () => import('./features/dashboard/dashboard.page').then(m => m.DashboardPage) },
      { path: 'clients', canActivate: [roleGuard('ADMIN', 'ACCOUNTANT', 'AUDITOR')], loadComponent: () => import('./features/clients/clients.page').then(m => m.ClientsPage) },
      { path: 'documents', loadComponent: () => import('./features/documents/documents.page').then(m => m.DocumentsPage) },
      { path: 'documents/:clientId/:documentId', loadComponent: () => import('./features/documents/document-review.page').then(m => m.DocumentReviewPage) },
      { path: 'expenses', canActivate: [ledgerGuard], loadComponent: () => import('./features/transactions/expenses.page').then(m => m.ExpensesPage) },
      { path: 'income', canActivate: [ledgerGuard], loadComponent: () => import('./features/transactions/income.page').then(m => m.IncomePage) },
      { path: 'banking', canActivate: [roleGuard('ADMIN', 'ACCOUNTANT', 'AUDITOR'), ledgerGuard], loadComponent: () => import('./features/banking/banking.page').then(m => m.BankingPage) },
      { path: 'close', canActivate: [roleGuard('ADMIN', 'ACCOUNTANT', 'AUDITOR'), ledgerGuard], loadComponent: () => import('./features/close/close.page').then(m => m.ClosePage) },
      { path: 'close/:clientId/:periodId', canActivate: [roleGuard('ADMIN', 'ACCOUNTANT', 'AUDITOR'), ledgerGuard], loadComponent: () => import('./features/close/period-detail.page').then(m => m.PeriodDetailPage) },
      { path: 'reports', canActivate: [ledgerGuard], loadComponent: () => import('./features/reports/reports.page').then(m => m.ReportsPage) },
      { path: 'reports/income', canActivate: [ledgerGuard], loadComponent: () => import('./features/reports/income-report.page').then(m => m.IncomeReportPage) },
      { path: 'reports/expenses', canActivate: [ledgerGuard], loadComponent: () => import('./features/reports/expense-report.page').then(m => m.ExpenseReportPage) },
      { path: 'reports/trends', canActivate: [ledgerGuard], loadComponent: () => import('./features/reports/trends.page').then(m => m.TrendsPage) },
      { path: 'users', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./features/admin/users.page').then(m => m.UsersPage) },
      { path: 'categories', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./features/admin/categories.page').then(m => m.CategoriesPage) },
      { path: 'audit', canActivate: [roleGuard('ADMIN', 'AUDITOR')], loadComponent: () => import('./features/admin/audit.page').then(m => m.AuditPage) },
      { path: 'firm', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./features/admin/firm.page').then(m => m.FirmPage) },
      { path: 'owner', canActivate: [ownerGuard], loadComponent: () => import('./features/owner/owner.page').then(m => m.OwnerPage) },
      { path: 'profile', loadComponent: () => import('./features/auth/profile.page').then(m => m.ProfilePage) }
    ]
  },
  { path: '**', redirectTo: 'app/dashboard' }
];
