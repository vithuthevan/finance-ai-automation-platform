import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <div class="page">
      <h1>Profile</h1>
      <mat-card class="metric-card">
        <p>{{ profile?.fullName }} · {{ profile?.email }} · {{ profile?.role }}</p>
      </mat-card>
      <h2>Change password</h2>
      <form [formGroup]="form" (ngSubmit)="changePassword()">
        <mat-form-field class="full-width"><mat-label>Current password</mat-label><input matInput type="password" formControlName="currentPassword"></mat-form-field>
        <mat-form-field class="full-width"><mat-label>New password</mat-label><input matInput type="password" formControlName="newPassword"></mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">Update password</button>
        @if (message) { <p>{{ message }}</p> }
      </form>
    </div>
  `
})
export class ProfilePage implements OnInit {
  profile: any;
  message = '';
  form = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]]
  });

  constructor(private api: ApiService, private fb: FormBuilder, readonly auth: AuthService) {}

  ngOnInit(): void {
    this.api.get('/api/v1/auth/me').subscribe((profile) => this.profile = profile);
  }

  changePassword(): void {
    this.api.post('/api/v1/users/me/password', this.form.getRawValue()).subscribe({
      next: () => this.message = 'Password updated',
      error: (err) => this.message = err?.error?.detail ?? 'Unable to update password'
    });
  }
}
