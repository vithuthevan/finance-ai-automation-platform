import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../shared/toast.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';

@Component({
  standalone: true,
  imports: [
    ReactiveFormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatButtonModule,
    PageHeaderComponent, StatusBadgeComponent, EmptyStateComponent
  ],
  template: `
    <div class="page">
      <app-page-header
        title="Clients"
        subtitle="Manage client records and contact details for your firm." />
      @if (auth.hasRole('ADMIN')) {
        <form class="toolbar-row" [formGroup]="form" (ngSubmit)="create()" novalidate>
          <mat-form-field>
            <mat-label>Name</mat-label>
            <input matInput formControlName="name">
            @if (form.controls.name.touched && form.controls.name.invalid) {
              <mat-error>Name is required</mat-error>
            }
          </mat-form-field>
          <mat-form-field>
            <mat-label>Email</mat-label>
            <input matInput type="email" formControlName="contactEmail">
            @if (form.controls.contactEmail.touched && form.controls.contactEmail.hasError('email')) {
              <mat-error>Enter a valid email address</mat-error>
            }
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || creating">
            {{ creating ? 'Creating…' : 'Create' }}
          </button>
        </form>
      }
      <div class="table-scroll">
        <table mat-table [dataSource]="clients" class="full-width">
          <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Name</th><td mat-cell *matCellDef="let row">{{ row.name }}</td></ng-container>
          <ng-container matColumnDef="email"><th mat-header-cell *matHeaderCellDef>Email</th><td mat-cell *matCellDef="let row">{{ row.contactEmail }}</td></ng-container>
          <ng-container matColumnDef="active"><th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let row">
              <app-status-badge [status]="row.active ? 'APPROVED' : 'VOID'" [label]="row.active ? 'Active' : 'Inactive'" />
            </td>
          </ng-container>
          <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let row">
              @if (auth.hasRole('ADMIN')) {
                <button mat-button (click)="toggle(row)" [disabled]="togglingId === row.id">
                  {{ togglingId === row.id ? 'Updating…' : (row.active ? 'Deactivate' : 'Activate') }}
                </button>
              }
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      </div>
      @if (!clients.length) {
        <app-empty-state
          title="No clients yet"
          description="Add your first business client to start collecting documents and transactions."
          icon="groups" />
      }
    </div>
  `,
  styles: [`
    .table-scroll {
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
    }
  `]
})
export class ClientsPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  readonly auth = inject(AuthService);
  clients: any[] = [];
  creating = false;
  togglingId: string | null = null;
  columns = ['name', 'email', 'active', 'actions'];
  form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    contactEmail: ['', Validators.email]
  });

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.api.list<any>('/api/v1/clients').subscribe((clients) => this.clients = clients);
  }

  create(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.creating) {
      return;
    }
    this.creating = true;
    this.api.post('/api/v1/clients', this.form.getRawValue()).subscribe({
      next: () => {
        this.form.reset();
        this.reload();
        this.toast.success('Client created');
        this.creating = false;
      },
      error: (err) => {
        this.toast.error(err?.error?.detail ?? 'Unable to create client');
        this.creating = false;
      }
    });
  }

  toggle(row: any): void {
    if (this.togglingId) {
      return;
    }
    const path = row.active ? 'deactivate' : 'activate';
    this.togglingId = row.id;
    this.api.post(`/api/v1/clients/${row.id}/${path}`).subscribe({
      next: () => {
        this.reload();
        this.toast.success(row.active ? 'Client deactivated' : 'Client activated');
        this.togglingId = null;
      },
      error: (err) => {
        this.toast.error(err?.error?.detail ?? 'Unable to update client');
        this.togglingId = null;
      }
    });
  }
}
