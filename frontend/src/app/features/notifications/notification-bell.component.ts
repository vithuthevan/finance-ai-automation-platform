import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { ApiService } from '../../core/services/api.service';
import { NotificationItem, UnreadCount } from '../notifications/notification.models';
import { interval, Subscription } from 'rxjs';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [MatBadgeModule, MatButtonModule, MatIconModule, MatMenuModule, RouterLink],
  template: `
    <button mat-icon-button [matMenuTriggerFor]="menu" aria-label="Notifications">
      <mat-icon [matBadge]="unread" matBadgeColor="warn" [matBadgeHidden]="unread === 0">notifications</mat-icon>
    </button>
    <mat-menu #menu="matMenu" class="notification-menu">
      @if (recent.length === 0) {
        <button mat-menu-item disabled>No notifications</button>
      }
      @for (item of recent; track item.id) {
        <button mat-menu-item (click)="open(item)">
          <div class="title">{{ item.title }}</div>
          <div class="meta">{{ item.message }}</div>
        </button>
      }
      <button mat-menu-item routerLink="/app/notifications">View all</button>
    </mat-menu>
  `,
  styles: [`
    .title { font-weight: 600; white-space: normal; }
    .meta { font-size: 12px; opacity: .8; white-space: normal; }
  `]
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  unread = 0;
  recent: NotificationItem[] = [];
  private poll?: Subscription;

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit(): void {
    this.refresh();
    this.poll = interval(60000).subscribe(() => this.refresh());
  }

  ngOnDestroy(): void {
    this.poll?.unsubscribe();
  }

  refresh(): void {
    this.api.get<UnreadCount>('/api/v1/notifications/unread-count').subscribe({
      next: (response) => this.unread = response.count,
      error: () => this.unread = 0
    });
    this.api.get<{ content: NotificationItem[] }>('/api/v1/notifications', { page: 0, size: 5 }).subscribe({
      next: (response) => this.recent = response.content ?? [],
      error: () => this.recent = []
    });
  }

  open(item: NotificationItem): void {
    if (!item.read) {
      this.api.post(`/api/v1/notifications/${item.id}/read`).subscribe(() => this.refresh());
    }
    if (item.actionUrl) {
      this.router.navigateByUrl(item.actionUrl);
    }
  }
}
