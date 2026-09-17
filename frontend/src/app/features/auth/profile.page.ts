import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../shared/toast.service';
import { matchingFieldValidator, PASSWORD_MIN_LENGTH, passwordValidators } from '../../shared/form.validators';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <div class="page">
      <h1>Profile</h1>
      <p class="page-subtitle">View your account details and update your password.</p>
      <mat-card class="metric-card">
        <p>{{ profile?.fullName }} · {{ profile?.email }} · {{ profile?.role }}</p>
      </mat-card>
      <section class="card-block">
        <h2>Change password</h2>
        <form [formGroup]="form" (ngSubmit)="changePassword()" novalidate>
          <div class="form-grid">
            <mat-form-field class="span-2">
              <mat-label>Current password</mat-label>
              <input matInput type="password" formControlName="currentPassword" autocomplete="current-password">
              @if (form.controls.currentPassword.touched && form.controls.currentPassword.invalid) {
                <mat-error>Current password is required</mat-error>
              }
            </mat-form-field>
            <mat-form-field>
              <mat-label>New password</mat-label>
              <input matInput type="password" formControlName="newPassword" autocomplete="new-password">
              @if (form.controls.newPassword.touched && form.controls.newPassword.hasError('required')) {
                <mat-error>New password is required</mat-error>
              } @else if (form.controls.newPassword.touched && form.controls.newPassword.hasError('minlength')) {
                <mat-error>Password must be at least {{ passwordMinLength }} characters</mat-error>
              }
            </mat-form-field>
            <mat-form-field>
              <mat-label>Confirm new password</mat-label>
              <input matInput type="password" formControlName="confirmNewPassword" autocomplete="new-password">
              @if (form.controls.confirmNewPassword.touched && form.controls.confirmNewPassword.hasError('required')) {
                <mat-error>Confirm your new password</mat-error>
              } @else if (form.controls.confirmNewPassword.touched && form.controls.confirmNewPassword.hasError('passwordMismatch')) {
                <mat-error>Passwords do not match</mat-error>
              }
            </mat-form-field>
          </div>
          <div class="form-actions">
            <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || changing">
              {{ changing ? 'Updating…' : 'Update password' }}
            </button>
          </div>
        </form>
      </section>
    </div>
  `
})
export class ProfilePage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  readonly auth = inject(AuthService);
  readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  profile: any;
  changing = false;
  form = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', passwordValidators],
    confirmNewPassword: ['', [Validators.required, matchingFieldValidator('newPassword')]]
  });

  ngOnInit(): void {
    this.api.get('/api/v1/auth/me').subscribe((profile) => this.profile = profile);
    this.form.controls.newPassword.valueChanges.subscribe(() => {
      this.form.controls.confirmNewPassword.updateValueAndValidity({ emitEvent: false });
    });
  }

  changePassword(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.changing) {
      return;
    }
    this.changing = true;
    const { confirmNewPassword: _, ...payload } = this.form.getRawValue();
    this.api.post('/api/v1/users/me/password', payload).subscribe({
      next: () => {
        this.toast.success('Password updated');
        this.form.reset();
        this.changing = false;
      },
      error: (err) => {
        this.toast.error(err?.error?.detail ?? 'Unable to update password');
        this.changing = false;
      }
    });
  }
}
