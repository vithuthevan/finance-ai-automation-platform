import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../../core/services/api.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  template: `
    <div class="page">
      <h1>Users</h1>
      <form class="toolbar-row" [formGroup]="form" (ngSubmit)="create()">
        <mat-form-field><mat-label>Name</mat-label><input matInput formControlName="fullName"></mat-form-field>
        <mat-form-field><mat-label>Email</mat-label><input matInput formControlName="email"></mat-form-field>
        <mat-form-field><mat-label>Password</mat-label><input matInput type="password" formControlName="password"></mat-form-field>
        <mat-form-field><mat-label>Role</mat-label>
          <mat-select formControlName="role">
            <mat-option value="ACCOUNTANT">Accountant</mat-option>
            <mat-option value="AUDITOR">Auditor</mat-option>
            <mat-option value="BUSINESS_OWNER">Business owner</mat-option>
          </mat-select>
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit">Create</button>
      </form>
      <table mat-table [dataSource]="users" class="full-width">
        <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Name</th><td mat-cell *matCellDef="let row">{{ row.fullName }}</td></ng-container>
        <ng-container matColumnDef="email"><th mat-header-cell *matHeaderCellDef>Email</th><td mat-cell *matCellDef="let row">{{ row.email }}</td></ng-container>
        <ng-container matColumnDef="role"><th mat-header-cell *matHeaderCellDef>Role</th><td mat-cell *matCellDef="let row">{{ row.role }}</td></ng-container>
        <ng-container matColumnDef="active"><th mat-header-cell *matHeaderCellDef>Active</th><td mat-cell *matCellDef="let row">{{ row.active ? 'Yes' : 'No' }}</td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            <button mat-button (click)="toggle(row)">{{ row.active ? 'Deactivate' : 'Activate' }}</button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>
  `
})
export class UsersPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  users: any[] = [];
  columns = ['name', 'email', 'role', 'active', 'actions'];
  form = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    role: ['ACCOUNTANT', Validators.required]
  });

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.api.list<any>('/api/v1/users').subscribe((users) => this.users = users);
  }

  create(): void {
    this.api.post('/api/v1/users', this.form.getRawValue()).subscribe(() => this.reload());
  }

  toggle(row: any): void {
    this.api.post(`/api/v1/users/${row.id}/${row.active ? 'deactivate' : 'activate'}`).subscribe(() => this.reload());
  }
}
