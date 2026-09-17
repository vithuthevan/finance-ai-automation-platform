import { Component, Input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [MatIconModule],
  template: `
    <div class="fp-empty" role="status">
      @if (icon) {
        <mat-icon class="fp-empty__icon" aria-hidden="true">{{ icon }}</mat-icon>
      }
      <p class="fp-empty__title">{{ title }}</p>
      @if (description) {
        <p class="fp-empty__desc">{{ description }}</p>
      }
      <div class="fp-empty__actions">
        <ng-content />
      </div>
    </div>
  `,
  styles: [`
    .fp-empty {
      text-align: center;
      padding: var(--fp-space-8) var(--fp-space-4);
      background: var(--fp-surface);
      border: 1px dashed var(--fp-border);
      border-radius: var(--fp-radius-md);
    }
    .fp-empty__icon {
      font-size: 40px;
      width: 40px;
      height: 40px;
      color: var(--fp-text-muted);
      margin-bottom: var(--fp-space-3);
    }
    .fp-empty__title {
      margin: 0;
      font-weight: var(--fp-weight-semibold);
      color: var(--fp-text-primary);
      font-size: var(--fp-text-card-title);
    }
    .fp-empty__desc {
      margin: var(--fp-space-2) auto 0;
      max-width: 28rem;
      font-size: var(--fp-text-body);
      color: var(--fp-text-muted);
      line-height: 1.5;
    }
    .fp-empty__actions {
      margin-top: var(--fp-space-4);
      display: flex;
      flex-wrap: wrap;
      gap: var(--fp-space-2);
      justify-content: center;
    }
  `]
})
export class EmptyStateComponent {
  @Input({ required: true }) title!: string;
  @Input() description = '';
  @Input() icon = 'inbox';
}
