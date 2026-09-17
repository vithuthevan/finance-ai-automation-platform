import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../core/auth/auth.service';
import { matchingFieldValidator, PASSWORD_MIN_LENGTH, passwordValidators } from '../../shared/form.validators';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <div class="auth-shell">
      <mat-card>
        <div class="brand-row"><span class="mark">◆</span><strong>Finance Platform</strong></div>
        <h1>Register firm</h1>
        <p>Create the firm administrator account, then sign in.</p>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <mat-form-field class="full-width">
            <mat-label>Firm name</mat-label>
            <input matInput formControlName="firmName">
            @if (form.controls.firmName.touched && form.controls.firmName.invalid) {
              <mat-error>Firm name is required</mat-error>
            }
          </mat-form-field>
          <mat-form-field class="full-width">
            <mat-label>Your name</mat-label>
            <input matInput formControlName="fullName">
            @if (form.controls.fullName.touched && form.controls.fullName.invalid) {
              <mat-error>Your name is required</mat-error>
            }
          </mat-form-field>
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
            <input matInput type="password" formControlName="password" autocomplete="new-password">
            @if (form.controls.password.touched && form.controls.password.hasError('required')) {
              <mat-error>Password is required</mat-error>
            } @else if (form.controls.password.touched && form.controls.password.hasError('minlength')) {
              <mat-error>Password must be at least {{ passwordMinLength }} characters</mat-error>
            }
          </mat-form-field>
          <mat-form-field class="full-width">
            <mat-label>Confirm password</mat-label>
            <input matInput type="password" formControlName="confirmPassword" autocomplete="new-password">
            @if (form.controls.confirmPassword.touched && form.controls.confirmPassword.hasError('required')) {
              <mat-error>Confirm your password</mat-error>
            } @else if (form.controls.confirmPassword.touched && form.controls.confirmPassword.hasError('passwordMismatch')) {
              <mat-error>Passwords do not match</mat-error>
            }
          </mat-form-field>
          @if (error) { <p class="error">{{ error }}</p> }
          <button mat-flat-button color="primary" class="full-width" type="submit" [disabled]="form.invalid || submitting">
            {{ submitting ? 'Creating firm…' : 'Create firm' }}
          </button>
        </form>
        <a routerLink="/login">Back to sign in</a>
      </mat-card>
    </div>
  `,
  styles: [`
    .brand-row { display:flex; align-items:center; gap:8px; margin-bottom:18px; color:var(--fp-ink); }
    .mark { width:28px; height:28px; border-radius:8px; display:grid; place-items:center; background:linear-gradient(145deg,#f29a4a,var(--fp-orange)); color:#fff; font-size:12px; }
  `]
})
export class RegisterPage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  error = '';
  submitting = false;
  form = this.fb.nonNullable.group({
    firmName: ['', Validators.required],
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', passwordValidators],
    confirmPassword: ['', [Validators.required, matchingFieldValidator('password')]]
  });

  ngOnInit(): void {
    this.form.controls.password.valueChanges.subscribe(() => {
      this.form.controls.confirmPassword.updateValueAndValidity({ emitEvent: false });
    });
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting) {
      return;
    }
    this.error = '';
    this.submitting = true;
    const { confirmPassword: _, ...payload } = this.form.getRawValue();
    this.auth.register(payload).subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: (err) => {
        this.error = err?.error?.detail ?? 'Registration failed';
        this.submitting = false;
      },
      complete: () => {
        this.submitting = false;
      }
    });
  }
}
