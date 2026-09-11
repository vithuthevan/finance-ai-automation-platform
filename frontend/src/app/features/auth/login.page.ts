import { Component, inject } from '@angular/core';
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
    <div class="auth-shell">
      <mat-card>
        <div class="brand-row"><span class="mark">◆</span><strong>Finance Platform</strong></div>
        <h1>Sign in</h1>
        <p>Continue to your firm workspace.</p>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field class="full-width" appearance="outline"><mat-label>Email</mat-label><input matInput formControlName="email" autocomplete="username"></mat-form-field>
          <mat-form-field class="full-width" appearance="outline"><mat-label>Password</mat-label><input matInput type="password" formControlName="password" autocomplete="current-password"></mat-form-field>
          @if (error) { <p class="error">{{ error }}</p> }
          <button mat-flat-button color="primary" class="full-width" type="submit" [disabled]="form.invalid">Sign in</button>
        </form>
        <a routerLink="/register">Register a firm</a>
      </mat-card>
    </div>
  `,
  styles: [`
    .brand-row { display:flex; align-items:center; gap:8px; margin-bottom:18px; color:var(--fp-ink); }
    .mark { width:28px; height:28px; border-radius:8px; display:grid; place-items:center; background:linear-gradient(145deg,#f29a4a,var(--fp-orange)); color:#fff; font-size:12px; }
  `]
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  error = '';
  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  submit(): void {
    this.error = '';
    this.auth.login(this.form.value.email!, this.form.value.password!).subscribe({
      next: () => this.router.navigateByUrl(this.auth.homePath()),
      error: () => this.error = 'Invalid credentials'
    });
  }
}
