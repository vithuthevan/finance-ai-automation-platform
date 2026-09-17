import { Component, Input, Output, EventEmitter } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-error-state',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  template: `
    <div class="fp-error" role="alert">
      <mat-icon aria-hidden="true">error_outline</mat-icon>
      <div>
        <p class="fp-error__title">{{ title }}</p>
        <p class="fp-error__msg">{{ message }}</p>
        @if (referenceId) {
          <p class="fp-error__ref">Reference: {{ referenceId }}</p>
        }
      </div>
      @if (showRetry) {
        <button mat-stroked-button type="button" (click)="retry.emit()">Try again</button>
      }
    </div>
  `,
  styles: [`
    .fp-error {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      gap: var(--fp-space-3);
      padding: var(--fp-space-4);
      background: var(--fp-danger-subtle);
      border: 1px solid #fecaca;
      border-radius: var(--fp-radius-md);
      color: var(--fp-danger);
    }
    .fp-error mat-icon { flex-shrink: 0; }
    .fp-error__title {
      margin: 0;
      font-weight: var(--fp-weight-semibold);
      color: var(--fp-text-primary);
    }
    .fp-error__msg {
      margin: var(--fp-space-1) 0 0;
      font-size: var(--fp-text-body);
      color: var(--fp-text-secondary);
    }
    .fp-error__ref {
      margin: var(--fp-space-2) 0 0;
      font-size: var(--fp-text-caption);
      color: var(--fp-text-muted);
      font-family: ui-monospace, monospace;
    }
  `]
})
export class ErrorStateComponent {
  @Input({ required: true }) title!: string;
  @Input() message = 'Something went wrong. Please try again.';
  @Input() referenceId = '';
  @Input() showRetry = true;
  @Output() retry = new EventEmitter<void>();
}
