import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { ApiService } from '../../core/services/api.service';
import { NotificationItem } from './notification.models';

@Component({
  standalone: true,
  imports: [MatButtonModule, MatCardModule, MatTabsModule, DatePipe],
  template: `
    <div class="page">
      <div class="toolbar-row">
        <h1>Notifications</h1>
        <button mat-stroked-button (click)="markAllRead()">Mark all read</button>
      </div>
      <mat-tab-group (selectedIndexChange)="onTab($event)">
        <mat-tab label="All"></mat-tab>
        <mat-tab label="Unread"></mat-tab>
        <mat-tab label="Read"></mat-tab>
      </mat-tab-group>
      @for (item of items; track item.id) {
        <mat-card class="metric-card" [class.unread]="!item.read" (click)="open(item)">
          <div class="label">{{ item.title }} · {{ item.createdAt | date:'short' }}</div>
          <div>{{ item.message }}</div>
        </mat-card>
      }
      @if (items.length === 0) {
        <p class="hint">No notifications in this view.</p>
      }
    </div>
  `,
  styles: [`
    .unread { border-left: 4px solid #3f51b5; cursor: pointer; }
    mat-card { cursor: pointer; margin-bottom: 8px; }
  `]
})
export class NotificationsPage implements OnInit {
  items: NotificationItem[] = [];
  filter: 'all' | 'unread' | 'read' = 'all';

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit(): void {
    this.load();
  }

  onTab(index: number): void {
    this.filter = index === 1 ? 'unread' : index === 2 ? 'read' : 'all';
    this.load();
  }

  load(): void {
    const params: Record<string, string | number | boolean> = { page: 0, size: 50 };
    if (this.filter === 'unread') {
      params['unreadOnly'] = true;
    }
    this.api.get<{ content: NotificationItem[] }>('/api/v1/notifications', params).subscribe({
      next: (response) => {
        let rows = response.content ?? [];
        if (this.filter === 'read') {
          rows = rows.filter((item) => item.read);
        }
        this.items = rows;
      }
    });
  }

  open(item: NotificationItem): void {
    if (!item.read) {
      this.api.post(`/api/v1/notifications/${item.id}/read`).subscribe(() => this.load());
    }
    if (item.actionUrl) {
      this.router.navigateByUrl(item.actionUrl);
    }
  }

  markAllRead(): void {
    this.api.post('/api/v1/notifications/read-all').subscribe(() => this.load());
  }
}
