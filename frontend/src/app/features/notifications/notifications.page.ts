import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { ApiService } from '../../core/services/api.service';
import { NotificationItem } from './notification.models';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';

@Component({
  standalone: true,
  imports: [MatButtonModule, MatCardModule, MatTabsModule, DatePipe, PageHeaderComponent, EmptyStateComponent],
  template: `
    <div class="page">
      <app-page-header title="Notifications" subtitle="Alerts and reminders from your firm workspace." />
      <div class="toolbar-row">
        <button mat-stroked-button (click)="markAllRead()">Mark all read</button>
      </div>
      <mat-tab-group (selectedIndexChange)="onTab($event)">
        <mat-tab label="All"></mat-tab>
        <mat-tab label="Unread"></mat-tab>
        <mat-tab label="Read"></mat-tab>
      </mat-tab-group>
      @for (item of items; track item.id) {
        <mat-card
          class="metric-card notification-card"
          [class.unread]="!item.read"
          role="button"
          tabindex="0"
          [attr.aria-label]="item.title + ': ' + item.message"
          (click)="open(item)"
          (keydown.enter)="onCardActivate($event, item)"
          (keydown.space)="onCardActivate($event, item)"
        >
          <div class="label">{{ item.title }} · {{ item.createdAt | date:'short' }}</div>
          <div>{{ item.message }}</div>
        </mat-card>
      }
      @if (items.length === 0) {
        <app-empty-state title="No notifications" description="You're all caught up in this view." icon="notifications" />
      }
    </div>
  `,
  styles: [`
    .notification-card {
      cursor: pointer;
      margin-bottom: 8px;
      transition: background 0.15s ease, border-color 0.15s ease;
    }

    .notification-card:hover {
      background: var(--fp-orange-soft);
    }

    .notification-card:focus-visible {
      outline: 2px solid var(--fp-orange);
      outline-offset: 2px;
    }

    .notification-card.unread {
      border-left: 4px solid var(--fp-orange);
    }
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

  onCardActivate(event: Event, item: NotificationItem): void {
    event.preventDefault();
    this.open(item);
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
