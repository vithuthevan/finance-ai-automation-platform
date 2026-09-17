import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../shared/toast.service';
import { matchingFieldValidator, PASSWORD_MIN_LENGTH, passwordValidators } from '../../shared/form.validators';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { LoadingStateComponent } from '../../shared/ui/loading-state.component';

@Component({
  standalone: true,
  imports: [
    ReactiveFormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    PageHeaderComponent, StatusBadgeComponent
  ],
  template: `
    <div class="page">
      <app-page-header title="Users" subtitle="Create firm users and manage their access." />
      <section class="card-block">
        <h2>Create user</h2>
        <form [formGroup]="form" (ngSubmit)="create()" novalidate>
          <div class="form-grid">
            <mat-form-field>
              <mat-label>Name</mat-label>
              <input matInput formControlName="fullName">
              @if (form.controls.fullName.touched && form.controls.fullName.invalid) {
                <mat-error>Name is required</mat-error>
              }
            </mat-form-field>
            <mat-form-field>
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email">
              @if (form.controls.email.touched && form.controls.email.hasError('required')) {
                <mat-error>Email is required</mat-error>
              } @else if (form.controls.email.touched && form.controls.email.hasError('email')) {
                <mat-error>Enter a valid email address</mat-error>
              }
            </mat-form-field>
            <mat-form-field>
              <mat-label>Password</mat-label>
              <input matInput type="password" formControlName="password" autocomplete="new-password">
              @if (form.controls.password.touched && form.controls.password.hasError('required')) {
                <mat-error>Password is required</mat-error>
              } @else if (form.controls.password.touched && form.controls.password.hasError('minlength')) {
                <mat-error>Password must be at least {{ passwordMinLength }} characters</mat-error>
              }
            </mat-form-field>
            <mat-form-field>
              <mat-label>Confirm password</mat-label>
              <input matInput type="password" formControlName="confirmPassword" autocomplete="new-password">
              @if (form.controls.confirmPassword.touched && form.controls.confirmPassword.hasError('required')) {
                <mat-error>Confirm the password</mat-error>
              } @else if (form.controls.confirmPassword.touched && form.controls.confirmPassword.hasError('passwordMismatch')) {
                <mat-error>Passwords do not match</mat-error>
              }
            </mat-form-field>
            <mat-form-field class="span-2">
              <mat-label>Role</mat-label>
              <mat-select formControlName="role">
                <mat-option value="ACCOUNTANT">Accountant</mat-option>
                <mat-option value="AUDITOR">Auditor</mat-option>
                <mat-option value="BUSINESS_OWNER">Business owner</mat-option>
              </mat-select>
              @if (form.controls.role.touched && form.controls.role.invalid) {
                <mat-error>Role is required</mat-error>
              }
            </mat-form-field>
          </div>
          <div class="form-actions">
            <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || creating">
              {{ creating ? 'Creating…' : 'Create user' }}
            </button>
          </div>
        </form>
      </section>
      <div class="table-scroll">
        <table mat-table [dataSource]="users" class="full-width">
          <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Name</th><td mat-cell *matCellDef="let row">{{ row.fullName }}</td></ng-container>
          <ng-container matColumnDef="email"><th mat-header-cell *matHeaderCellDef>Email</th><td mat-cell *matCellDef="let row">{{ row.email }}</td></ng-container>
          <ng-container matColumnDef="role"><th mat-header-cell *matHeaderCellDef>Role</th>
            <td mat-cell *matCellDef="let row"><app-status-badge [status]="'PENDING'" [label]="roleLabel(row.role)" /></td>
          </ng-container>
          <ng-container matColumnDef="active"><th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let row">
              <app-status-badge [status]="row.active ? 'APPROVED' : 'VOID'" [label]="row.active ? 'Active' : 'Inactive'" />
            </td>
          </ng-container>
          <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let row">
              <button mat-button (click)="toggle(row)">{{ row.active ? 'Deactivate' : 'Activate' }}</button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      </div>
    </div>
  `,
  styles: [`
    .table-scroll {
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
    }
  `]
})
export class UsersPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  users: any[] = [];
  columns = ['name', 'email', 'role', 'active', 'actions'];
  creating = false;
  form = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', passwordValidators],
    confirmPassword: ['', [Validators.required, matchingFieldValidator('password')]],
    role: ['ACCOUNTANT', Validators.required]
  });

  ngOnInit(): void {
    this.reload();
    this.form.controls.password.valueChanges.subscribe(() => {
      this.form.controls.confirmPassword.updateValueAndValidity({ emitEvent: false });
    });
  }

  reload(): void {
    this.api.list<any>('/api/v1/users').subscribe((users) => this.users = users);
  }

  create(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.creating) {
      return;
    }
    this.creating = true;
    const { confirmPassword: _, ...payload } = this.form.getRawValue();
    this.api.post('/api/v1/users', payload).subscribe({
      next: () => {
        this.form.reset({ role: 'ACCOUNTANT' });
        this.reload();
        this.toast.success('User created');
        this.creating = false;
      },
      error: (err) => {
        this.toast.error(err?.error?.detail ?? 'Unable to create user');
        this.creating = false;
      }
    });
  }

  toggle(row: any): void {
    this.api.post(`/api/v1/users/${row.id}/${row.active ? 'deactivate' : 'activate'}`).subscribe({
      next: () => this.reload(),
      error: (err) => this.toast.error(err?.error?.detail ?? 'Unable to update user')
    });
  }

  roleLabel(role: string): string {
    return role?.replace(/_/g, ' ') ?? '';
  }
}
