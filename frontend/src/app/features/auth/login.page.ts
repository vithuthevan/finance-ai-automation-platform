import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  template: `
    <div class="auth-shell">
      <mat-card>
        <div class="brand-row"><span class="mark" aria-hidden="true"><mat-icon>account_balance</mat-icon></span><strong>Finance Platform</strong></div>
        <h1>Sign in</h1>
        <p>Continue to your firm workspace.</p>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <mat-form-field class="full-width">
            <mat-label>Email</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="username">
            @if (form.controls.email.touched && form.controls.email.hasError('required')) {
              <mat-error>Email is required</mat-error>
            } @else if (form.controls.email.touched && form.controls.email.hasError('email')) {
              <mat-error>Enter a valid email address</mat-error>
            }
          </mat-form-field>
          <mat-form-field class="full-width">
            <mat-label>Password</mat-label>
            <input matInput [type]="hidePassword ? 'password' : 'text'" formControlName="password" autocomplete="current-password">
            <button mat-icon-button matSuffix type="button" (click)="hidePassword = !hidePassword"
              [attr.aria-label]="hidePassword ? 'Show password' : 'Hide password'">
              <mat-icon>{{ hidePassword ? 'visibility' : 'visibility_off' }}</mat-icon>
            </button>
            @if (form.controls.password.touched && form.controls.password.invalid) {
              <mat-error>Password is required</mat-error>
            }
          </mat-form-field>
          @if (error) { <p class="error">{{ error }}</p> }
          <button mat-flat-button color="primary" class="full-width" type="submit" [disabled]="form.invalid || submitting">
            {{ submitting ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>
        <a routerLink="/register">Register a firm</a>
      </mat-card>
    </div>
  `,
  styles: [`
    .brand-row { display:flex; align-items:center; gap:8px; margin-bottom:18px; color:var(--fp-ink); }
    .mark { width:32px; height:32px; border-radius:var(--fp-radius-md); display:grid; place-items:center; background:var(--fp-primary); color:#fff; }
    .mark mat-icon { font-size:18px; width:18px; height:18px; }
  `]
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  error = '';
  submitting = false;
  hidePassword = true;
  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting) {
      return;
    }
    this.error = '';
    this.submitting = true;
    this.auth.login(this.form.value.email!, this.form.value.password!).subscribe({
      next: () => this.router.navigateByUrl(this.auth.homePath()),
      error: () => {
        this.error = 'Invalid credentials';
        this.submitting = false;
      },
      complete: () => {
        this.submitting = false;
      }
    });
  }
}
