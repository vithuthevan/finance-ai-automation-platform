import { Component } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <div class="auth">
      <mat-card>
        <h1>Register firm</h1>
        <p>Create the firm administrator account, then sign in.</p>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field class="full-width"><mat-label>Firm name</mat-label><input matInput formControlName="firmName"></mat-form-field>
          <mat-form-field class="full-width"><mat-label>Your name</mat-label><input matInput formControlName="fullName"></mat-form-field>
          <mat-form-field class="full-width"><mat-label>Email</mat-label><input matInput formControlName="email"></mat-form-field>
          <mat-form-field class="full-width"><mat-label>Password</mat-label><input matInput type="password" formControlName="password"></mat-form-field>
          @if (error) { <p class="error">{{ error }}</p> }
          <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">Create firm</button>
        </form>
        <a routerLink="/login">Back to sign in</a>
      </mat-card>
    </div>
  `,
  styles: [`.auth { min-height:100vh; display:grid; place-items:center; } mat-card { width: 420px; padding: 24px; } .error { color:#9b1c1c; }`]
})
export class RegisterPage {
  error = '';
  form = this.fb.nonNullable.group({
    firmName: ['', Validators.required],
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  constructor(private fb: FormBuilder, private auth: AuthService, private router: Router) {}

  submit(): void {
    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: (err) => this.error = err?.error?.detail ?? 'Registration failed'
    });
  }
}
