import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../../core/services/api.service';
import { ToastService } from '../../shared/toast.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  template: `
    <div class="page">
      <h1>Categories</h1>
      <p class="page-subtitle">Define expense and income categories used when creating transactions.</p>
      <section class="card-block">
        <h2>Create category</h2>
        <form [formGroup]="form" (ngSubmit)="create()" novalidate>
          <div class="form-grid">
            <mat-form-field>
              <mat-label>Code</mat-label>
              <input matInput formControlName="code">
              @if (form.controls.code.touched && form.controls.code.hasError('required')) {
                <mat-error>Code is required</mat-error>
              } @else if (form.controls.code.touched && form.controls.code.hasError('maxlength')) {
                <mat-error>Code must not exceed 30 characters</mat-error>
              }
            </mat-form-field>
            <mat-form-field>
              <mat-label>Name</mat-label>
              <input matInput formControlName="name">
              @if (form.controls.name.touched && form.controls.name.hasError('required')) {
                <mat-error>Name is required</mat-error>
              } @else if (form.controls.name.touched && form.controls.name.hasError('maxlength')) {
                <mat-error>Name must not exceed 100 characters</mat-error>
              }
            </mat-form-field>
            <mat-form-field class="span-2">
              <mat-label>Type</mat-label>
              <mat-select formControlName="categoryType">
                <mat-option value="EXPENSE">Expense</mat-option>
                <mat-option value="INCOME">Income</mat-option>
                <mat-option value="BOTH">Both</mat-option>
              </mat-select>
              @if (form.controls.categoryType.touched && form.controls.categoryType.invalid) {
                <mat-error>Type is required</mat-error>
              }
            </mat-form-field>
          </div>
          <div class="form-actions">
            <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || creating">
              {{ creating ? 'Creating…' : 'Create category' }}
            </button>
          </div>
        </form>
      </section>
      <div class="table-scroll">
        <table mat-table [dataSource]="categories" class="full-width">
          <ng-container matColumnDef="code"><th mat-header-cell *matHeaderCellDef>Code</th><td mat-cell *matCellDef="let row">{{ row.code }}</td></ng-container>
          <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Name</th><td mat-cell *matCellDef="let row">{{ row.name }}</td></ng-container>
          <ng-container matColumnDef="type"><th mat-header-cell *matHeaderCellDef>Type</th><td mat-cell *matCellDef="let row">{{ row.categoryType }}</td></ng-container>
          <ng-container matColumnDef="active"><th mat-header-cell *matHeaderCellDef>Active</th><td mat-cell *matCellDef="let row">{{ row.active ? 'Yes' : 'No' }}</td></ng-container>
          <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let row">
              <button mat-button type="button" [disabled]="togglingId === row.id" (click)="toggle(row)">
                {{ togglingId === row.id ? 'Updating…' : (row.active ? 'Deactivate' : 'Activate') }}
              </button>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      </div>
    </div>
  `,
  styles: [`
    .table-scroll {
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
    }
  `]
})
export class CategoriesPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  categories: any[] = [];
  columns = ['code', 'name', 'type', 'active', 'actions'];
  creating = false;
  togglingId: string | null = null;
  form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(30)]],
    name: ['', [Validators.required, Validators.maxLength(100)]],
    categoryType: ['EXPENSE', Validators.required]
  });

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.api.get<any[]>('/api/v1/categories').subscribe((categories) => this.categories = categories);
  }

  create(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.creating) {
      return;
    }
    this.creating = true;
    this.api.post('/api/v1/categories', this.form.getRawValue()).subscribe({
      next: () => {
        this.form.reset({ categoryType: 'EXPENSE' });
        this.reload();
        this.toast.success('Category created');
        this.creating = false;
      },
      error: (err) => {
        this.toast.error(err?.error?.detail ?? 'Unable to create category');
        this.creating = false;
      }
    });
  }

  toggle(row: any): void {
    if (this.togglingId) {
      return;
    }
    this.togglingId = row.id;
    this.api.post(`/api/v1/categories/${row.id}/${row.active ? 'deactivate' : 'activate'}`).subscribe({
      next: () => {
        this.reload();
        this.togglingId = null;
      },
      error: (err) => {
        this.toast.error(err?.error?.detail ?? 'Unable to update category');
        this.togglingId = null;
      }
    });
  }
}
