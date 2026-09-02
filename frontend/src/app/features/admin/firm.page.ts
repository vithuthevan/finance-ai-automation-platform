import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTabsModule } from '@angular/material/tabs';
import { ApiService } from '../../core/services/api.service';

@Component({
  standalone: true,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSlideToggleModule,
    MatTabsModule
  ],
  template: `
    <div class="page">
      <h1>Firm settings</h1>
      <mat-tab-group>
        <mat-tab label="General">
          <form [formGroup]="form" (ngSubmit)="save()" class="card-block">
            <mat-form-field class="full-width"><mat-label>Name</mat-label><input matInput formControlName="name"></mat-form-field>
            <mat-form-field class="full-width"><mat-label>Currency</mat-label><input matInput formControlName="currencyCode"></mat-form-field>
            <mat-form-field class="full-width"><mat-label>Timezone</mat-label><input matInput formControlName="timezone"></mat-form-field>
            <button mat-flat-button color="primary" type="submit">Save</button>
            @if (message) { <p class="hint">{{ message }}</p> }
          </form>
        </mat-tab>
        <mat-tab label="Accounting">
          <form [formGroup]="form" (ngSubmit)="save()" class="card-block">
            <mat-form-field class="full-width"><mat-label>Financial year start month</mat-label><input matInput type="number" min="1" max="12" formControlName="financialYearStartMonth"></mat-form-field>
            <p class="hint">Month-end close still uses calendar months. This setting prepares future fiscal-year reporting.</p>
            <button mat-flat-button color="primary" type="submit">Save</button>
          </form>
        </mat-tab>
        <mat-tab label="Automation">
          <form [formGroup]="form" (ngSubmit)="save()" class="card-block">
            <mat-slide-toggle formControlName="aiEnabled">AI assistance enabled</mat-slide-toggle>
            <p class="hint">When disabled, documents remain fully available for manual review and bookkeeping.</p>
            @if (metrics) {
              <p class="hint">Processed {{ metrics.documentsProcessed }} · Success {{ metrics.successes }} · Failed {{ metrics.failures }}</p>
            }
            <div class="toolbar-row"><button mat-flat-button color="primary" type="submit">Save</button></div>
          </form>
        </mat-tab>
        <mat-tab label="Subscription">
          <div class="card-block">
            <p>View plan limits, usage, and upgrade requests on the subscription page.</p>
            <a mat-flat-button color="primary" routerLink="/app/subscription">Open subscription</a>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `
})
export class FirmPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  metrics: any;
  message = '';
  form = this.fb.nonNullable.group({
    name: [''],
    currencyCode: ['LKR'],
    timezone: ['Asia/Colombo'],
    financialYearStartMonth: [4],
    aiEnabled: [true]
  });

  ngOnInit(): void {
    this.api.get<any>('/api/v1/settings/firm').subscribe((firm) => this.form.patchValue(firm));
    this.api.get<any>('/api/v1/ai/metrics').subscribe({
      next: (metrics) => this.metrics = metrics,
      error: () => this.metrics = null
    });
  }

  save(): void {
    this.message = '';
    this.api.put('/api/v1/settings/firm', this.form.getRawValue()).subscribe({
      next: () => this.message = 'Settings saved.',
      error: () => this.message = 'Could not save settings.'
    });
  }
}
