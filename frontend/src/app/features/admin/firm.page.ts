import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { ApiService } from '../../core/services/api.service';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatSlideToggleModule],
  template: `
    <div class="page">
      <h1>Firm settings</h1>
      <form [formGroup]="form" (ngSubmit)="save()">
        <mat-form-field class="full-width"><mat-label>Name</mat-label><input matInput formControlName="name"></mat-form-field>
        <mat-form-field class="full-width"><mat-label>Currency</mat-label><input matInput formControlName="currencyCode"></mat-form-field>
        <mat-form-field class="full-width"><mat-label>Timezone</mat-label><input matInput formControlName="timezone"></mat-form-field>
        <mat-form-field class="full-width"><mat-label>Financial year start month</mat-label><input matInput type="number" formControlName="financialYearStartMonth"></mat-form-field>
        <mat-slide-toggle formControlName="aiEnabled">AI extraction enabled</mat-slide-toggle>
        <div class="toolbar-row"><button mat-flat-button color="primary" type="submit">Save</button></div>
      </form>
      @if (metrics) {
        <h2>AI processing</h2>
        <p class="hint">Processed {{ metrics.documentsProcessed }} · Success {{ metrics.successes }} · Failed {{ metrics.failures }} · Accepted {{ metrics.suggestionsAccepted }} · Modified {{ metrics.suggestionsModified }} · Rejected {{ metrics.suggestionsRejected }}</p>
      }
    </div>
  `
})
export class FirmPage implements OnInit {
  metrics: any;
  form = this.fb.nonNullable.group({
    name: [''],
    currencyCode: ['LKR'],
    timezone: ['Asia/Colombo'],
    financialYearStartMonth: [4],
    aiEnabled: [true]
  });

  constructor(private api: ApiService, private fb: FormBuilder) {}

  ngOnInit(): void {
    this.api.get<any>('/api/v1/firm').subscribe((firm) => this.form.patchValue(firm));
    this.api.get<any>('/api/v1/ai/metrics').subscribe({
      next: (metrics) => this.metrics = metrics,
      error: () => this.metrics = null
    });
  }

  save(): void {
    this.api.put('/api/v1/firm', this.form.getRawValue()).subscribe();
  }
}
