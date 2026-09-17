import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ApiService } from '../../core/services/api.service';
import { PlatformFirmDetail } from './platform.models';

const TRIAL_EXTENSION_MIN_DAYS = 1;
const TRIAL_EXTENSION_MAX_DAYS = 365;
const REASON_MAX_LENGTH = 2000;

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

        <section class="card-block">
          <h2>Change plan</h2>
          <form [formGroup]="planForm" (ngSubmit)="changePlan()" novalidate>
            <div class="form-grid">
              <mat-form-field>
                <mat-label>Plan</mat-label>
                <mat-select formControlName="planCode">
                  <mat-option value="STARTER">Starter</mat-option>
                  <mat-option value="PRACTICE">Practice</mat-option>
                  <mat-option value="PROFESSIONAL">Professional</mat-option>
                </mat-select>
              </mat-form-field>
              <mat-form-field class="span-2">
                <mat-label>Reason</mat-label>
                <textarea matInput rows="2" formControlName="reason"></textarea>
                @if (planForm.controls.reason.touched && planForm.controls.reason.hasError('required')) {
                  <mat-error>A reason is required for plan changes</mat-error>
                } @else if (planForm.controls.reason.touched && planForm.controls.reason.hasError('maxlength')) {
                  <mat-error>Reason must not exceed {{ reasonMaxLength }} characters</mat-error>
                }
              </mat-form-field>
            </div>
            <div class="form-actions">
              <button mat-flat-button color="primary" type="submit" [disabled]="planForm.invalid || changingPlan">
                {{ changingPlan ? 'Applying…' : 'Apply plan' }}
              </button>
            </div>
          </form>
        </section>

        <section class="card-block">
          <h2>Subscription status</h2>
          <form [formGroup]="statusForm" (ngSubmit)="changeStatus()" novalidate>
            <div class="form-grid">
              <mat-form-field>
                <mat-label>Status</mat-label>
                <mat-select formControlName="status">
                  <mat-option value="TRIAL">Trial</mat-option>
                  <mat-option value="ACTIVE">Active</mat-option>
                  <mat-option value="SUSPENDED">Suspended</mat-option>
                  <mat-option value="CANCELLED">Cancelled</mat-option>
                </mat-select>
              </mat-form-field>
              <mat-form-field class="span-2">
                <mat-label>Reason</mat-label>
                <textarea matInput rows="2" formControlName="reason"></textarea>
                @if (statusForm.controls.reason.touched && statusForm.controls.reason.hasError('required')) {
                  <mat-error>A reason is required for status changes</mat-error>
                } @else if (statusForm.controls.reason.touched && statusForm.controls.reason.hasError('maxlength')) {
                  <mat-error>Reason must not exceed {{ reasonMaxLength }} characters</mat-error>
                }
              </mat-form-field>
            </div>
            <div class="form-actions">
              <button mat-flat-button color="primary" type="submit" [disabled]="statusForm.invalid || changingStatus">
                {{ changingStatus ? 'Updating…' : 'Update status' }}
              </button>
            </div>
          </form>
        </section>

        <section class="card-block">
          <h2>Extend trial</h2>
          <form [formGroup]="trialForm" (ngSubmit)="extendTrial()" novalidate>
            <div class="form-grid">
              <mat-form-field>
                <mat-label>Additional days</mat-label>
                <input matInput type="number" formControlName="additionalDays" [min]="trialExtensionMinDays" [max]="trialExtensionMaxDays">
                @if (trialForm.controls.additionalDays.touched && trialForm.controls.additionalDays.hasError('required')) {
                  <mat-error>Additional days is required</mat-error>
                } @else if (trialForm.controls.additionalDays.touched && trialForm.controls.additionalDays.hasError('min')) {
                  <mat-error>Enter at least {{ trialExtensionMinDays }} day</mat-error>
                } @else if (trialForm.controls.additionalDays.touched && trialForm.controls.additionalDays.hasError('max')) {
                  <mat-error>Cannot extend by more than {{ trialExtensionMaxDays }} days</mat-error>
                }
              </mat-form-field>
              <mat-form-field class="span-2">
                <mat-label>Reason</mat-label>
                <textarea matInput rows="2" formControlName="reason"></textarea>
                @if (trialForm.controls.reason.touched && trialForm.controls.reason.hasError('required')) {
                  <mat-error>A reason is required for trial extensions</mat-error>
                } @else if (trialForm.controls.reason.touched && trialForm.controls.reason.hasError('maxlength')) {
                  <mat-error>Reason must not exceed {{ reasonMaxLength }} characters</mat-error>
                }
              </mat-form-field>
            </div>
            <div class="form-actions">
              <button mat-flat-button color="primary" type="submit" [disabled]="trialForm.invalid || extendingTrial">
                {{ extendingTrial ? 'Extending…' : 'Extend trial' }}
              </button>
            </div>
          </form>
        </section>

        @if (message) { <p class="hint">{{ message }}</p> }
      }
    </div>
  `
})
export class PlatformFirmPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  readonly trialExtensionMinDays = TRIAL_EXTENSION_MIN_DAYS;
  readonly trialExtensionMaxDays = TRIAL_EXTENSION_MAX_DAYS;
  readonly reasonMaxLength = REASON_MAX_LENGTH;

  firm: PlatformFirmDetail | null = null;
  message = '';
  firmId = '';
  changingPlan = false;
  changingStatus = false;
  extendingTrial = false;

  private readonly reasonValidators = [
    Validators.required,
    Validators.maxLength(REASON_MAX_LENGTH)
  ];

  planForm = this.fb.nonNullable.group({
    planCode: ['PRACTICE'],
    reason: ['', this.reasonValidators]
  });

  statusForm = this.fb.nonNullable.group({
    status: ['ACTIVE'],
    reason: ['', this.reasonValidators]
  });

  trialForm = this.fb.nonNullable.group({
    additionalDays: [
      14,
      [Validators.required, Validators.min(TRIAL_EXTENSION_MIN_DAYS), Validators.max(TRIAL_EXTENSION_MAX_DAYS)]
    ],
    reason: ['', this.reasonValidators]
  });

  ngOnInit(): void {
    this.firmId = this.route.snapshot.paramMap.get('firmId') ?? '';
    this.load();
  }

  changePlan(): void {
    if (this.planForm.invalid || this.changingPlan) {
      this.planForm.markAllAsTouched();
      return;
    }
    const { planCode, reason } = this.planForm.getRawValue();
    const trimmedReason = reason.trim();
    if (!trimmedReason) {
      this.planForm.controls.reason.setErrors({ required: true });
      this.planForm.controls.reason.markAsTouched();
      return;
    }
    this.message = '';
    this.changingPlan = true;
    this.api.put<PlatformFirmDetail>(`/api/v1/platform/firms/${this.firmId}/subscription/plan`, {
      planCode,
      reason: trimmedReason
    }).subscribe({
      next: (firm) => {
        this.firm = firm;
        this.planForm.patchValue({ planCode: firm.planCode ?? 'STARTER' });
        this.planForm.controls.reason.reset('');
        this.message = 'Plan updated.';
        this.changingPlan = false;
      },
      error: () => {
        this.message = 'Plan update failed.';
        this.changingPlan = false;
      }
    });
  }

  changeStatus(): void {
    if (this.statusForm.invalid || this.changingStatus) {
      this.statusForm.markAllAsTouched();
      return;
    }
    const { status, reason } = this.statusForm.getRawValue();
    const trimmedReason = reason.trim();
    if (!trimmedReason) {
      this.statusForm.controls.reason.setErrors({ required: true });
      this.statusForm.controls.reason.markAsTouched();
      return;
    }
    this.message = '';
    this.changingStatus = true;
    this.api.put<PlatformFirmDetail>(`/api/v1/platform/firms/${this.firmId}/subscription/status`, {
      status,
      reason: trimmedReason
    }).subscribe({
      next: (firm) => {
        this.firm = firm;
        this.statusForm.patchValue({ status: firm.status ?? 'ACTIVE' });
        this.statusForm.controls.reason.reset('');
        this.message = 'Status updated.';
        this.changingStatus = false;
      },
      error: () => {
        this.message = 'Status update failed.';
        this.changingStatus = false;
      }
    });
  }

  extendTrial(): void {
    if (this.trialForm.invalid || this.extendingTrial) {
      this.trialForm.markAllAsTouched();
      return;
    }
    const { additionalDays, reason } = this.trialForm.getRawValue();
    const trimmedReason = reason.trim();
    if (!trimmedReason) {
      this.trialForm.controls.reason.setErrors({ required: true });
      this.trialForm.controls.reason.markAsTouched();
      return;
    }
    this.message = '';
    this.extendingTrial = true;
    this.api.post<PlatformFirmDetail>(`/api/v1/platform/firms/${this.firmId}/subscription/extend-trial`, {
      additionalDays,
      reason: trimmedReason
    }).subscribe({
      next: (firm) => {
        this.firm = firm;
        this.trialForm.controls.reason.reset('');
        this.message = 'Trial extended.';
        this.extendingTrial = false;
      },
      error: () => {
        this.message = 'Trial extension failed.';
        this.extendingTrial = false;
      }
    });
  }

  private load(): void {
    this.api.get<PlatformFirmDetail>(`/api/v1/platform/firms/${this.firmId}`).subscribe((firm) => {
      this.firm = firm;
      this.planForm.patchValue({ planCode: firm.planCode ?? 'STARTER' });
      this.statusForm.patchValue({ status: firm.status ?? 'ACTIVE' });
    });
  }
}
