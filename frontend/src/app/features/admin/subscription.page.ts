import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { ApiService } from '../../core/services/api.service';
import { PlanChangeRequest, SubscriptionSummary, SubscriptionUsage } from './subscription.models';

@Component({
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule
  ],
  template: `
    <div class="page">
      <h1>Subscription &amp; usage</h1>
      @if (usage && !usage.writeAllowed) {
        <div class="subscription-banner warning">
          Your subscription is not active for write operations. Existing data remains available in read-only mode.
        </div>
      }
      @if (summary) {
        <mat-card class="card-block">
          <div class="subscription-header">
            <div>
              <div class="plan-name">{{ summary.planCode }}</div>
              <div class="hint">Status: {{ summary.status }}</div>
              @if (summary.periodFrom && summary.periodTo) {
                <div class="hint">Billing period: {{ summary.periodFrom }} — {{ summary.periodTo }}</div>
              }
              @if (summary.trialEndsAt) {
                <div class="hint">Trial ends: {{ summary.trialEndsAt }}</div>
              }
            </div>
          </div>
        </mat-card>
      }
      @if (usage) {
        <h2>Usage this period</h2>
        <div class="grid-2">
          @for (metric of metrics(usage); track metric.key) {
            <mat-card class="metric-card usage-card">
              <div class="label">{{ metric.label }}</div>
              <div class="value">{{ metric.usedLabel }} of {{ metric.limitLabel }}</div>
              <mat-progress-bar mode="determinate" [value]="metric.percent" [color]="metric.color"></mat-progress-bar>
              @if (metric.percent >= 80) {
                <div class="usage-warning">{{ metric.percent }}% used</div>
              }
            </mat-card>
          }
        </div>
      }
      <h2>Request plan change</h2>
      <p class="hint">Plan upgrades are reviewed manually. No payment is collected through this application.</p>
      <form [formGroup]="upgradeForm" (ngSubmit)="requestUpgrade()" class="card-block">
        <mat-form-field class="full-width">
          <mat-label>Requested plan</mat-label>
          <mat-select formControlName="requestedPlanCode">
            <mat-option value="STARTER">Starter</mat-option>
            <mat-option value="PRACTICE">Practice</mat-option>
            <mat-option value="PROFESSIONAL">Professional</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field class="full-width">
          <mat-label>Note (optional)</mat-label>
          <textarea matInput rows="3" formControlName="note"></textarea>
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="upgradeForm.invalid">Request upgrade</button>
        @if (upgradeMessage) {
          <p class="hint">{{ upgradeMessage }}</p>
        }
      </form>
    </div>
  `,
  styles: [`
    .plan-name { font-size: 28px; font-weight: 600; }
    .usage-card mat-progress-bar { margin-top: 8px; }
    .usage-warning { color: #b45309; font-size: 12px; margin-top: 6px; }
  `]
})
export class SubscriptionPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  usage: SubscriptionUsage | null = null;
  summary: SubscriptionSummary | null = null;
  upgradeMessage = '';
  upgradeForm = this.fb.nonNullable.group({
    requestedPlanCode: ['PRACTICE'],
    note: ['']
  });

  ngOnInit(): void {
    this.api.get<SubscriptionSummary>('/api/v1/subscription').subscribe((summary) => this.summary = summary);
    this.api.get<SubscriptionUsage>('/api/v1/subscription/usage').subscribe((usage) => this.usage = usage);
  }

  requestUpgrade(): void {
    this.upgradeMessage = '';
    this.api.post<PlanChangeRequest>('/api/v1/subscription/upgrade-request', this.upgradeForm.getRawValue()).subscribe({
      next: () => this.upgradeMessage = 'Upgrade request submitted. A platform administrator will review it.',
      error: () => this.upgradeMessage = 'Could not submit upgrade request.'
    });
  }

  metrics(usage: SubscriptionUsage) {
    return [
      this.metric('clients', 'Active clients', usage.clients, (v) => String(v)),
      this.metric('users', 'Active users', usage.users, (v) => String(v)),
      this.metric('documents', 'Documents', usage.documents, (v) => String(v)),
      this.metric('ai', 'AI processing', usage.aiProcessing, (v) => String(v)),
      this.metric('storage', 'Storage', usage.storageBytes, (v) => formatBytes(v))
    ];
  }

  private metric(
    key: string,
    label: string,
    limit: { used: number; limit: number; percentUsed: number },
    format: (value: number) => string
  ) {
    const percent = limit.percentUsed ?? Math.min(100, Math.round(limit.used * 100 / Math.max(limit.limit, 1)));
    return {
      key,
      label,
      usedLabel: format(limit.used),
      limitLabel: format(limit.limit),
      percent,
      color: percent >= 100 ? 'warn' : percent >= 90 ? 'accent' : 'primary'
    };
  }
}

function formatBytes(bytes: number): string {
  if (bytes >= 1073741824) {
    return (bytes / 1073741824).toFixed(1) + ' GB';
  }
  if (bytes >= 1048576) {
    return (bytes / 1048576).toFixed(1) + ' MB';
  }
  return bytes + ' B';
}
