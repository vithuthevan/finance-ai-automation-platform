import { Component, Input } from '@angular/core';

const LABELS: Record<string, string> = {
  DRAFT: 'Draft',
  APPROVED: 'Approved',
  VOID: 'Void',
  CLOSED: 'Closed',
  NEEDS_REVIEW: 'Needs review',
  UPLOADED: 'Uploaded',
  LINKED: 'Linked',
  REJECTED: 'Rejected',
  READY: 'Ready',
  OPEN: 'Open',
  IN_REVIEW: 'In review',
  REOPENED: 'Reopened',
  MATCHED: 'Matched',
  UNMATCHED: 'Unmatched',
  SUGGESTED: 'Suggested',
  IGNORED: 'Ignored',
  COMPLETED: 'Completed',
  PENDING: 'Pending',
  PROCESSING: 'Processing',
  EXTRACTED: 'Extracted',
  FAILED: 'Failed'
};

type BadgeTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

const TONE: Record<string, BadgeTone> = {
  DRAFT: 'warning',
  APPROVED: 'success',
  VOID: 'danger',
  CLOSED: 'neutral',
  NEEDS_REVIEW: 'warning',
  UPLOADED: 'info',
  LINKED: 'success',
  REJECTED: 'danger',
  READY: 'success',
  OPEN: 'info',
  IN_REVIEW: 'warning',
  REOPENED: 'info',
  MATCHED: 'success',
  UNMATCHED: 'warning',
  SUGGESTED: 'info',
  IGNORED: 'neutral',
  COMPLETED: 'success',
  PENDING: 'neutral',
  PROCESSING: 'info',
  EXTRACTED: 'success',
  FAILED: 'danger'
};

@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `
    <span class="fp-badge" [class]="'fp-badge--' + tone" [attr.title]="status">
      <span class="fp-badge__dot" aria-hidden="true"></span>
      {{ displayLabel }}
    </span>
  `,
  styles: [`
    .fp-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 2px 10px;
      border-radius: var(--fp-radius-full);
      font-size: var(--fp-text-caption);
      font-weight: var(--fp-weight-semibold);
      letter-spacing: 0.02em;
      line-height: 1.4;
      border: 1px solid transparent;
      white-space: nowrap;
    }
    .fp-badge__dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      flex-shrink: 0;
    }
    .fp-badge--success {
      color: var(--fp-success);
      background: var(--fp-success-subtle);
      border-color: #a7f3d0;
    }
    .fp-badge--success .fp-badge__dot { background: var(--fp-success); }
    .fp-badge--warning {
      color: var(--fp-warning);
      background: var(--fp-warning-subtle);
      border-color: #fde68a;
    }
    .fp-badge--warning .fp-badge__dot { background: var(--fp-warning); }
    .fp-badge--danger {
      color: var(--fp-danger);
      background: var(--fp-danger-subtle);
      border-color: #fecaca;
    }
    .fp-badge--danger .fp-badge__dot { background: var(--fp-danger); }
    .fp-badge--info {
      color: var(--fp-info);
      background: var(--fp-info-subtle);
      border-color: #bfdbfe;
    }
    .fp-badge--info .fp-badge__dot { background: var(--fp-info); }
    .fp-badge--neutral {
      color: var(--fp-neutral);
      background: var(--fp-neutral-subtle);
      border-color: var(--fp-border);
    }
    .fp-badge--neutral .fp-badge__dot { background: var(--fp-neutral); }
  `]
})
export class StatusBadgeComponent {
  @Input({ required: true }) status!: string;
  @Input() label = '';

  get tone(): BadgeTone {
    return TONE[this.status] ?? 'neutral';
  }

  get displayLabel(): string {
    return this.label || LABELS[this.status] || this.status.replace(/_/g, ' ');
  }
}
