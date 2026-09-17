import { Pipe, PipeTransform } from '@angular/core';
import { formatMoney } from './money';

@Pipe({ name: 'money', standalone: true })
export class MoneyPipe implements PipeTransform {
  transform(value: unknown, currencyCode?: string): string {
    return formatMoney(value, currencyCode);
  }
}
