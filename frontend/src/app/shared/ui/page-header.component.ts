import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="fp-page-header">
      <div class="fp-page-header__text">
        @if (backLink) {
          <a class="fp-page-header__back" [routerLink]="backLink">{{ backLabel }}</a>
        }
        <h1 class="fp-page-header__title">{{ title }}</h1>
        @if (subtitle) {
          <p class="fp-page-header__subtitle">{{ subtitle }}</p>
        }
      </div>
      <div class="fp-page-header__actions">
        <ng-content select="[fpPageActions]" />
      </div>
    </header>
  `,
  styles: [`
    .fp-page-header {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--fp-space-4);
      margin-bottom: var(--fp-space-5);
    }
    .fp-page-header__title {
      margin: 0;
      font-family: var(--fp-font-display);
      font-size: var(--fp-text-page-title);
      font-weight: var(--fp-weight-bold);
      letter-spacing: -0.02em;
      color: var(--fp-text-primary);
      line-height: 1.25;
    }
    .fp-page-header__subtitle {
      margin: var(--fp-space-2) 0 0;
      font-size: var(--fp-text-body);
      color: var(--fp-text-muted);
      max-width: 52rem;
      line-height: 1.5;
    }
    .fp-page-header__back {
      display: inline-block;
      margin-bottom: var(--fp-space-2);
      font-size: var(--fp-text-body);
      font-weight: var(--fp-weight-semibold);
      color: var(--fp-primary);
      text-decoration: none;
    }
    .fp-page-header__back:hover { text-decoration: underline; }
    .fp-page-header__actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--fp-space-2);
      align-items: center;
      flex-shrink: 0;
    }
    @media (max-width: 768px) {
      .fp-page-header__actions {
        width: 100%;
      }
      .fp-page-header__actions ::ng-deep .mat-mdc-button-base {
        flex: 1 1 auto;
      }
    }
  `]
})
export class PageHeaderComponent {
  @Input({ required: true }) title!: string;
  @Input() subtitle = '';
  @Input() backLink: string | null = null;
  @Input() backLabel = '← Back';
}
