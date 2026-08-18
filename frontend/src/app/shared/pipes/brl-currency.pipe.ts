import { Pipe, PipeTransform } from '@angular/core';

import {
  formatBrl,
  MoneyInput,
} from '../utils/money.util';

@Pipe({
  name: 'brlCurrency',
  standalone: true,
})
export class BrlCurrencyPipe implements PipeTransform {
  transform(value: MoneyInput): string {
    return formatBrl(value);
  }
}