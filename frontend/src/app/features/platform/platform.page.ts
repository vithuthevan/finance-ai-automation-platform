import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { ApiService } from '../../core/services/api.service';
import { PlatformFirm, PlatformMetrics } from './platform.models';

@Component({
  standalone: true,
  imports: [RouterLink, MatCardModule],
  template: `
    <div class="page">
      <h1>Platform administration</h1>
      <p class="hint">Operational SaaS metrics only. Client financial records are not exposed here.</p>
      @if (metrics) {
        <div class="grid-2">
          <mat-card class="metric-card"><div class="label">Total firms</div><div class="value">{{ metrics.totalFirms }}</div></mat-card>
          <mat-card class="metric-card"><div class="label">Active subscriptions</div><div class="value">{{ metrics.activeSubscriptions }}</div></mat-card>
          <mat-card class="metric-card"><div class="label">Trial firms</div><div class="value">{{ metrics.trialFirms }}</div></mat-card>
          <mat-card class="metric-card"><div class="label">Suspended firms</div><div class="value">{{ metrics.suspendedFirms }}</div></mat-card>
        </div>
      }
      <h2>Firms</h2>
      <table class="data-table">
        <thead><tr><th>Name</th><th>Plan</th><th>Status</th><th></th></tr></thead>
        <tbody>
          @for (firm of firms; track firm.id) {
            <tr>
              <td>{{ firm.name }}</td>
              <td>{{ firm.planCode || '—' }}</td>
              <td>{{ firm.status || '—' }}</td>
              <td><a routerLink="/platform/firms/{{ firm.id }}">Manage</a></td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `
})
export class PlatformPage implements OnInit {
  metrics: PlatformMetrics | null = null;
  firms: PlatformFirm[] = [];

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<PlatformMetrics>('/api/v1/platform/metrics').subscribe((metrics) => this.metrics = metrics);
    this.api.get<PlatformFirm[]>('/api/v1/platform/firms').subscribe((firms) => this.firms = firms);
  }
}
