import { Component, Input } from '@angular/core';
import { MoneyPipe } from '../money.pipe';

@Component({
  selector: 'app-money-display',
  standalone: true,
  imports: [MoneyPipe],
  template: `<span class="fp-money" [class.fp-money--emphasis]="emphasis">{{ value | money:currency }}</span>`,
  styles: [`
    .fp-money {
      font-variant-numeric: tabular-nums;
      font-feature-settings: 'tnum';
      font-weight: var(--fp-weight-medium);
      color: var(--fp-text-primary);
    }
    .fp-money--emphasis {
      font-family: var(--fp-font-display);
      font-size: var(--fp-text-financial);
      font-weight: var(--fp-weight-semibold);
    }
  `]
})
export class MoneyDisplayComponent {
  @Input({ required: true }) value!: unknown;
  @Input() currency = '';
  @Input() emphasis = false;
}
