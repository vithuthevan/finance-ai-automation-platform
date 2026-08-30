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
        <h1>Sign in</h1>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field class="full-width"><mat-label>Email</mat-label><input matInput formControlName="email"></mat-form-field>
          <mat-form-field class="full-width"><mat-label>Password</mat-label><input matInput type="password" formControlName="password"></mat-form-field>
          @if (error) { <p class="error">{{ error }}</p> }
          <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">Sign in</button>
        </form>
        <a routerLink="/register">Register a firm</a>
      </mat-card>
    </div>
  `,
  styles: [`.auth { min-height:100vh; display:grid; place-items:center; } mat-card { width: 380px; padding: 24px; } .error { color:#9b1c1c; }`]
})
export class LoginPage {
  error = '';
  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  constructor(private fb: FormBuilder, private auth: AuthService, private router: Router) {}

  submit(): void {
    this.error = '';
    this.auth.login(this.form.value.email!, this.form.value.password!).subscribe({
      next: () => this.router.navigateByUrl(this.auth.homePath()),
      error: () => this.error = 'Invalid credentials'
    });
  }
}
