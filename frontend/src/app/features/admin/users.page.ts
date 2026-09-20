import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../shared/toast.service';
import { matchingFieldValidator, PASSWORD_MIN_LENGTH, passwordValidators } from '../../shared/form.validators';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';

interface UserRow {
  id: string;
  fullName: string;
  email: string;
  role: string;
  active: boolean;
  clientAccess?: { clientId: string; accessType: string }[];
}

@Component({
  standalone: true,
  imports: [
    ReactiveFormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule,
    MatCheckboxModule, PageHeaderComponent, StatusBadgeComponent
  ],
  template: `
    <div class="page">
      <app-page-header title="Users" subtitle="Create firm users, assign roles, and control which clients each user can access." />
      <section class="card-block">
        <h2>Create user</h2>
        <form [formGroup]="form" (ngSubmit)="create()" novalidate>
          <div class="form-grid">
            <mat-form-field>
              <mat-label>Name</mat-label>
              <input matInput formControlName="fullName">
            </mat-form-field>
            <mat-form-field>
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email">
            </mat-form-field>
            <mat-form-field>
              <mat-label>Password</mat-label>
              <input matInput type="password" formControlName="password" autocomplete="new-password">
            </mat-form-field>
            <mat-form-field>
              <mat-label>Confirm password</mat-label>
              <input matInput type="password" formControlName="confirmPassword" autocomplete="new-password">
            </mat-form-field>
            <mat-form-field class="span-2">
              <mat-label>Role</mat-label>
              <mat-select formControlName="role">
                <mat-option value="ACCOUNTANT">Accountant</mat-option>
                <mat-option value="AUDITOR">Auditor</mat-option>
                <mat-option value="BUSINESS_OWNER">Business owner</mat-option>
              </mat-select>
            </mat-form-field>
          </div>
          @if (needsClientAccess(form.controls.role.value)) {
            <div class="access-picker">
              <p class="hint">Select clients this user may access.</p>
              @for (client of clients; track client.id) {
                <mat-checkbox
                  [checked]="createClientIds.has(client.id)"
                  (change)="toggleCreateClient(client.id, $event.checked)">
                  {{ client.name }}
                </mat-checkbox>
              }
            </div>
          }
          <div class="form-actions">
            <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || creating">
              {{ creating ? 'Creating…' : 'Create user' }}
            </button>
          </div>
        </form>
      </section>

      @if (editingUser) {
        <section class="card-block">
          <h2>Client access — {{ editingUser.fullName }}</h2>
          <p class="hint">Changes apply immediately for non-admin roles.</p>
          @for (client of clients; track client.id) {
            <mat-checkbox
              [checked]="editClientIds.has(client.id)"
              (change)="toggleEditClient(client.id, $event.checked)">
              {{ client.name }}
            </mat-checkbox>
          }
          <div class="form-actions">
            <button mat-stroked-button type="button" (click)="cancelEdit()">Cancel</button>
            <button mat-flat-button color="primary" type="button" (click)="saveAccess()" [disabled]="savingAccess">
              {{ savingAccess ? 'Saving…' : 'Save access' }}
            </button>
          </div>
        </section>
      }

      <div class="table-scroll">
        <table mat-table [dataSource]="users" class="full-width">
          <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Name</th><td mat-cell *matCellDef="let row">{{ row.fullName }}</td></ng-container>
          <ng-container matColumnDef="email"><th mat-header-cell *matHeaderCellDef>Email</th><td mat-cell *matCellDef="let row">{{ row.email }}</td></ng-container>
          <ng-container matColumnDef="role"><th mat-header-cell *matHeaderCellDef>Role</th>
            <td mat-cell *matCellDef="let row"><app-status-badge [status]="'PENDING'" [label]="roleLabel(row.role)" /></td>
          </ng-container>
          <ng-container matColumnDef="access"><th mat-header-cell *matHeaderCellDef>Client access</th>
            <td mat-cell *matCellDef="let row">{{ accessSummary(row) }}</td>
          </ng-container>
          <ng-container matColumnDef="active"><th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let row">
              <app-status-badge [status]="row.active ? 'APPROVED' : 'VOID'" [label]="row.active ? 'Active' : 'Inactive'" />
            </td>
          </ng-container>
          <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let row">
              @if (needsClientAccess(row.role)) {
                <button mat-button type="button" (click)="startEdit(row)">Access</button>
              }
              <button mat-button (click)="toggle(row)">{{ row.active ? 'Deactivate' : 'Activate' }}</button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      </div>
    </div>
  `,
  styles: [`
    .table-scroll { overflow-x: auto; }
    .access-picker { display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; }
  `]
})
export class UsersPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  readonly passwordMinLength = PASSWORD_MIN_LENGTH;
  users: UserRow[] = [];
  clients: { id: string; name: string }[] = [];
  columns = ['name', 'email', 'role', 'access', 'active', 'actions'];
  creating = false;
  savingAccess = false;
  editingUser: UserRow | null = null;
  createClientIds = new Set<string>();
  editClientIds = new Set<string>();
  form = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', passwordValidators],
    confirmPassword: ['', [Validators.required, matchingFieldValidator('password')]],
    role: ['ACCOUNTANT', Validators.required]
  });

  ngOnInit(): void {
    this.reload();
    this.api.list<{ id: string; name: string }>('/api/v1/clients').subscribe((clients) => this.clients = clients);
    this.form.controls.password.valueChanges.subscribe(() => {
      this.form.controls.confirmPassword.updateValueAndValidity({ emitEvent: false });
    });
  }

  reload(): void {
    this.api.get<{ content: UserRow[] }>('/api/v1/users', { page: 0, size: 100 }).subscribe({
      next: (res) => this.users = res.content ?? [],
      error: () => this.api.list<UserRow>('/api/v1/users').subscribe((users) => this.users = users)
    });
  }

  needsClientAccess(role: string): boolean {
    return role === 'BUSINESS_OWNER' || role === 'ACCOUNTANT' || role === 'AUDITOR';
  }

  accessSummary(row: UserRow): string {
    if (row.role === 'ADMIN') {
      return 'All clients';
    }
    const count = row.clientAccess?.length ?? 0;
    return count ? `${count} client(s)` : 'None assigned';
  }

  toggleCreateClient(id: string, checked: boolean): void {
    if (checked) {
      this.createClientIds.add(id);
    } else {
      this.createClientIds.delete(id);
    }
  }

  create(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.creating) {
      return;
    }
    this.creating = true;
    const { confirmPassword: _, ...raw } = this.form.getRawValue();
    const payload: Record<string, unknown> = { ...raw };
    if (this.needsClientAccess(raw.role) && this.createClientIds.size) {
      payload['clientAccess'] = [...this.createClientIds].map((clientId) => ({ clientId, accessType: 'FULL' }));
    }
    this.api.post('/api/v1/users', payload).subscribe({
      next: () => {
        this.form.reset({ role: 'ACCOUNTANT' });
        this.createClientIds.clear();
        this.reload();
        this.toast.success('User created');
        this.creating = false;
      },
      error: (err) => {
        this.toast.error(err?.error?.detail ?? 'Unable to create user');
        this.creating = false;
      }
    });
  }

  startEdit(row: UserRow): void {
    this.editingUser = row;
    this.editClientIds = new Set((row.clientAccess ?? []).map((a) => a.clientId));
  }

  cancelEdit(): void {
    this.editingUser = null;
    this.editClientIds.clear();
  }

  toggleEditClient(id: string, checked: boolean): void {
    if (checked) {
      this.editClientIds.add(id);
    } else {
      this.editClientIds.delete(id);
    }
  }

  saveAccess(): void {
    if (!this.editingUser || this.savingAccess) {
      return;
    }
    this.savingAccess = true;
    const assignments = [...this.editClientIds].map((clientId) => ({ clientId, accessType: 'FULL' }));
    this.api.put(`/api/v1/users/${this.editingUser.id}/client-access`, { assignments }).subscribe({
      next: () => {
        this.toast.success('Client access updated');
        this.savingAccess = false;
        this.cancelEdit();
        this.reload();
      },
      error: (err) => {
        this.toast.error(err?.error?.detail ?? 'Unable to update access');
        this.savingAccess = false;
      }
    });
  }

  toggle(row: UserRow): void {
    this.api.post(`/api/v1/users/${row.id}/${row.active ? 'deactivate' : 'activate'}`).subscribe({
      next: () => this.reload(),
      error: (err) => this.toast.error(err?.error?.detail ?? 'Unable to update user')
    });
  }

  roleLabel(role: string): string {
    return role?.replace(/_/g, ' ') ?? '';
  }
}
