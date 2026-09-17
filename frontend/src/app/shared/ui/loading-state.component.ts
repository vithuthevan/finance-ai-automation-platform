import { Component, Input } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-loading-state',
  standalone: true,
  imports: [MatProgressSpinnerModule],
  template: `
    <div class="fp-loading" role="status" [attr.aria-label]="message">
      <mat-spinner diameter="36" />
      <p>{{ message }}</p>
    </div>
  `,
  styles: [`
    .fp-loading {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: var(--fp-space-3);
      padding: var(--fp-space-8);
      color: var(--fp-text-muted);
      font-size: var(--fp-text-body);
    }
    .fp-loading p { margin: 0; }
  `]
})
export class LoadingStateComponent {
  @Input() message = 'Loading…';
}
