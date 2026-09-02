import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ApiService } from '../../core/services/api.service';
import { PlatformFirmDetail } from './platform.models';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, MatCardModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <div class="page">
      @if (firm) {
        <h1>{{ firm.name }}</h1>
        <p class="hint">Plan {{ firm.planCode }} · {{ firm.status }}</p>
        <mat-card class="card-block">
          <div class="grid-2">
            <div><div class="label">Clients</div><div class="value">{{ firm.usage.clients.used }} / {{ firm.usage.clients.limit }}</div></div>
            <div><div class="label">Users</div><div class="value">{{ firm.usage.users.used }} / {{ firm.usage.users.limit }}</div></div>
            <div><div class="label">Documents</div><div class="value">{{ firm.usage.documents.used }} / {{ firm.usage.documents.limit }}</div></div>
            <div><div class="label">AI processing</div><div class="value">{{ firm.usage.aiProcessing.used }} / {{ firm.usage.aiProcessing.limit }}</div></div>
          </div>
        </mat-card>
        <h2>Change plan</h2>
        <form [formGroup]="planForm" (ngSubmit)="changePlan()" class="card-block toolbar-row">
          <mat-form-field>
            <mat-label>Plan</mat-label>
            <mat-select formControlName="planCode">
              <mat-option value="STARTER">Starter</mat-option>
              <mat-option value="PRACTICE">Practice</mat-option>
              <mat-option value="PROFESSIONAL">Professional</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field class="full-width"><mat-label>Reason</mat-label><input matInput formControlName="reason"></mat-form-field>
          <button mat-flat-button color="primary" type="submit">Apply plan</button>
        </form>
        <h2>Subscription status</h2>
        <form [formGroup]="statusForm" (ngSubmit)="changeStatus()" class="card-block toolbar-row">
          <mat-form-field>
            <mat-label>Status</mat-label>
            <mat-select formControlName="status">
              <mat-option value="TRIAL">Trial</mat-option>
              <mat-option value="ACTIVE">Active</mat-option>
              <mat-option value="SUSPENDED">Suspended</mat-option>
              <mat-option value="CANCELLED">Cancelled</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field class="full-width"><mat-label>Reason</mat-label><input matInput formControlName="reason"></mat-form-field>
          <button mat-flat-button color="primary" type="submit">Update status</button>
        </form>
        <h2>Extend trial</h2>
        <form [formGroup]="trialForm" (ngSubmit)="extendTrial()" class="card-block toolbar-row">
          <mat-form-field><mat-label>Additional days</mat-label><input matInput type="number" formControlName="additionalDays"></mat-form-field>
          <mat-form-field class="full-width"><mat-label>Reason</mat-label><input matInput formControlName="reason"></mat-form-field>
          <button mat-flat-button color="primary" type="submit">Extend trial</button>
        </form>
        @if (message) { <p class="hint">{{ message }}</p> }
      }
    </div>
  `
})
export class PlatformFirmPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  firm: PlatformFirmDetail | null = null;
  message = '';
  firmId = '';
  planForm = this.fb.nonNullable.group({ planCode: ['PRACTICE'], reason: [''] });
  statusForm = this.fb.nonNullable.group({ status: ['ACTIVE'], reason: [''] });
  trialForm = this.fb.nonNullable.group({ additionalDays: [14], reason: [''] });

  ngOnInit(): void {
    this.firmId = this.route.snapshot.paramMap.get('firmId') ?? '';
    this.load();
  }

  changePlan(): void {
    this.api.put<PlatformFirmDetail>(`/api/v1/platform/firms/${this.firmId}/subscription/plan`, this.planForm.getRawValue())
      .subscribe({ next: (firm) => { this.firm = firm; this.message = 'Plan updated.'; }, error: () => this.message = 'Plan update failed.' });
  }

  changeStatus(): void {
    this.api.put<PlatformFirmDetail>(`/api/v1/platform/firms/${this.firmId}/subscription/status`, this.statusForm.getRawValue())
      .subscribe({ next: (firm) => { this.firm = firm; this.message = 'Status updated.'; }, error: () => this.message = 'Status update failed.' });
  }

  extendTrial(): void {
    this.api.post<PlatformFirmDetail>(`/api/v1/platform/firms/${this.firmId}/subscription/extend-trial`, this.trialForm.getRawValue())
      .subscribe({ next: (firm) => { this.firm = firm; this.message = 'Trial extended.'; }, error: () => this.message = 'Trial extension failed.' });
  }

  private load(): void {
    this.api.get<PlatformFirmDetail>(`/api/v1/platform/firms/${this.firmId}`).subscribe((firm) => {
      this.firm = firm;
      this.planForm.patchValue({ planCode: firm.planCode ?? 'STARTER' });
      this.statusForm.patchValue({ status: firm.status ?? 'ACTIVE' });
    });
  }
}
