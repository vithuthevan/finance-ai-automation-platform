import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <div class="page">
      <h1>Clients</h1>
      @if (auth.hasRole('ADMIN')) {
        <form class="toolbar-row" [formGroup]="form" (ngSubmit)="create()">
          <mat-form-field><mat-label>Name</mat-label><input matInput formControlName="name"></mat-form-field>
          <mat-form-field><mat-label>Email</mat-label><input matInput formControlName="contactEmail"></mat-form-field>
          <button mat-flat-button color="primary" type="submit">Create</button>
        </form>
      }
      <table mat-table [dataSource]="clients" class="full-width">
        <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Name</th><td mat-cell *matCellDef="let row">{{ row.name }}</td></ng-container>
        <ng-container matColumnDef="email"><th mat-header-cell *matHeaderCellDef>Email</th><td mat-cell *matCellDef="let row">{{ row.contactEmail }}</td></ng-container>
        <ng-container matColumnDef="active"><th mat-header-cell *matHeaderCellDef>Active</th><td mat-cell *matCellDef="let row">{{ row.active ? 'Yes' : 'No' }}</td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            @if (auth.hasRole('ADMIN')) {
              <button mat-button (click)="toggle(row)">{{ row.active ? 'Deactivate' : 'Activate' }}</button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    </div>
  `
})
export class ClientsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);
  clients: any[] = [];
  columns = ['name', 'email', 'active', 'actions'];
  form = this.fb.nonNullable.group({ name: ['', Validators.required], contactEmail: [''] });

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.api.list<any>('/api/v1/clients').subscribe((clients) => this.clients = clients);
  }

  create(): void {
    this.api.post('/api/v1/clients', this.form.getRawValue()).subscribe(() => {
      this.form.reset();
      this.reload();
    });
  }

  toggle(row: any): void {
    const path = row.active ? 'deactivate' : 'activate';
    this.api.post(`/api/v1/clients/${row.id}/${path}`).subscribe(() => this.reload());
  }
}
