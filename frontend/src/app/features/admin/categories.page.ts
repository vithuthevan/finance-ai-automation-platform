import { Component, OnInit } from '@angular/core';
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
      <h1>Categories</h1>
      <form class="toolbar-row" [formGroup]="form" (ngSubmit)="create()">
        <mat-form-field><mat-label>Code</mat-label><input matInput formControlName="code"></mat-form-field>
        <mat-form-field><mat-label>Name</mat-label><input matInput formControlName="name"></mat-form-field>
        <mat-form-field><mat-label>Type</mat-label>
          <mat-select formControlName="categoryType">
            <mat-option value="EXPENSE">Expense</mat-option>
            <mat-option value="INCOME">Income</mat-option>
            <mat-option value="BOTH">Both</mat-option>
          </mat-select>
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit">Create</button>
      </form>
      <table mat-table [dataSource]="categories" class="full-width">
        <ng-container matColumnDef="code"><th mat-header-cell *matHeaderCellDef>Code</th><td mat-cell *matCellDef="let row">{{ row.code }}</td></ng-container>
        <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Name</th><td mat-cell *matCellDef="let row">{{ row.name }}</td></ng-container>
        <ng-container matColumnDef="type"><th mat-header-cell *matHeaderCellDef>Type</th><td mat-cell *matCellDef="let row">{{ row.categoryType }}</td></ng-container>
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
export class CategoriesPage implements OnInit {
  categories: any[] = [];
  columns = ['code', 'name', 'type', 'active', 'actions'];
  form = this.fb.nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
    categoryType: ['EXPENSE', Validators.required]
  });

  constructor(private api: ApiService, private fb: FormBuilder) {}

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.api.get<any[]>('/api/v1/categories').subscribe((categories) => this.categories = categories);
  }

  create(): void {
    this.api.post('/api/v1/categories', this.form.getRawValue()).subscribe(() => this.reload());
  }

  toggle(row: any): void {
    this.api.post(`/api/v1/categories/${row.id}/${row.active ? 'deactivate' : 'activate'}`).subscribe(() => this.reload());
  }
}
