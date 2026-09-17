import { Component, inject, OnInit } from '@angular/core';

import { RouterLink } from '@angular/router';

import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { MatButtonModule } from '@angular/material/button';

import { MatFormFieldModule } from '@angular/material/form-field';

import { MatInputModule } from '@angular/material/input';

import { MatSlideToggleModule } from '@angular/material/slide-toggle';

import { MatTabsModule } from '@angular/material/tabs';

import { ApiService } from '../../core/services/api.service';

import { ToastService } from '../../shared/toast.service';

import { currencyCodeValidators, timezoneValidator } from '../../shared/form.validators';



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

          <section class="card-block" [formGroup]="form">

            <div class="form-grid">

              <mat-form-field class="span-2">

                <mat-label>Name</mat-label>

                <input matInput formControlName="name">

                @if (form.controls.name.touched && form.controls.name.hasError('required')) {

                  <mat-error>Name is required</mat-error>

                } @else if (form.controls.name.touched && form.controls.name.hasError('maxlength')) {

                  <mat-error>Name must not exceed 200 characters</mat-error>

                }

              </mat-form-field>

              <mat-form-field>

                <mat-label>Currency</mat-label>

                <input matInput formControlName="currencyCode" maxlength="3">

                @if (form.controls.currencyCode.touched && form.controls.currencyCode.hasError('required')) {

                  <mat-error>Currency is required</mat-error>

                } @else if (form.controls.currencyCode.touched && form.controls.currencyCode.hasError('pattern')) {

                  <mat-error>Use a 3-letter currency code</mat-error>

                }

              </mat-form-field>

              <mat-form-field>

                <mat-label>Timezone</mat-label>

                <input matInput formControlName="timezone">

                @if (form.controls.timezone.touched && form.controls.timezone.hasError('required')) {

                  <mat-error>Timezone is required</mat-error>

                } @else if (form.controls.timezone.touched && form.controls.timezone.hasError('invalidTimezone')) {

                  <mat-error>Enter a valid IANA timezone</mat-error>

                }

              </mat-form-field>

            </div>

            <div class="form-actions">

              <button mat-flat-button color="primary" type="button"

                [disabled]="generalSectionInvalid || saving"

                (click)="saveGeneral()">

                {{ saving ? 'Saving…' : 'Save general settings' }}

              </button>

            </div>

          </section>

        </mat-tab>

        <mat-tab label="Accounting">

          <section class="card-block" [formGroup]="form">

            <mat-form-field class="full-width">

              <mat-label>Financial year start month</mat-label>

              <input matInput type="number" formControlName="financialYearStartMonth">

              @if (form.controls.financialYearStartMonth.touched && form.controls.financialYearStartMonth.hasError('required')) {

                <mat-error>Month is required</mat-error>

              } @else if (form.controls.financialYearStartMonth.touched && (form.controls.financialYearStartMonth.hasError('min') || form.controls.financialYearStartMonth.hasError('max'))) {

                <mat-error>Enter a month from 1 to 12</mat-error>

              }

            </mat-form-field>

            <p class="hint">Month-end close still uses calendar months. This setting prepares future fiscal-year reporting.</p>

            <div class="form-actions">

              <button mat-flat-button color="primary" type="button"

                [disabled]="form.controls.financialYearStartMonth.invalid || saving"

                (click)="saveAccounting()">

                {{ saving ? 'Saving…' : 'Save accounting settings' }}

              </button>

            </div>

          </section>

        </mat-tab>

        <mat-tab label="Automation">

          <section class="card-block" [formGroup]="form">

            <mat-slide-toggle formControlName="aiEnabled">AI assistance enabled</mat-slide-toggle>

            <p class="hint">When disabled, documents remain fully available for manual review and bookkeeping.</p>

            @if (metrics) {

              <p class="hint">Processed {{ metrics.documentsProcessed }} · Success {{ metrics.successes }} · Failed {{ metrics.failures }}</p>

            }

            <div class="form-actions">

              <button mat-flat-button color="primary" type="button"

                [disabled]="saving"

                (click)="saveAutomation()">

                {{ saving ? 'Saving…' : 'Save automation settings' }}

              </button>

            </div>

          </section>

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

  private readonly toast = inject(ToastService);

  metrics: any;

  saving = false;

  form = this.fb.nonNullable.group({

    name: ['', [Validators.required, Validators.maxLength(200)]],

    currencyCode: ['LKR', currencyCodeValidators],

    timezone: ['Asia/Colombo', [Validators.required, timezoneValidator]],

    financialYearStartMonth: [4, [Validators.required, Validators.min(1), Validators.max(12)]],

    aiEnabled: [true]

  });



  get generalSectionInvalid(): boolean {

    return this.form.controls.name.invalid

      || this.form.controls.currencyCode.invalid

      || this.form.controls.timezone.invalid;

  }



  ngOnInit(): void {

    this.api.get<any>('/api/v1/settings/firm').subscribe((firm) => this.form.patchValue(firm));

    this.api.get<any>('/api/v1/ai/metrics').subscribe({

      next: (metrics) => this.metrics = metrics,

      error: () => this.metrics = null

    });

  }



  saveGeneral(): void {

    this.form.controls.name.markAsTouched();

    this.form.controls.currencyCode.markAsTouched();

    this.form.controls.timezone.markAsTouched();

    if (this.generalSectionInvalid || this.saving) {

      return;

    }

    this.persistSettings('General settings saved.');

  }



  saveAccounting(): void {

    this.form.controls.financialYearStartMonth.markAsTouched();

    if (this.form.controls.financialYearStartMonth.invalid || this.saving) {

      return;

    }

    this.persistSettings('Accounting settings saved.');

  }



  saveAutomation(): void {

    if (this.saving) {

      return;

    }

    this.persistSettings('Automation settings saved.');

  }



  private persistSettings(successMessage: string): void {

    this.saving = true;

    const raw = this.form.getRawValue();

    this.api.put('/api/v1/settings/firm', {

      ...raw,

      currencyCode: raw.currencyCode.trim().toUpperCase()

    }).subscribe({

      next: () => {

        this.toast.success(successMessage);

        this.saving = false;

      },

      error: () => {

        this.toast.error('Could not save settings.');

        this.saving = false;

      }

    });

  }

}


