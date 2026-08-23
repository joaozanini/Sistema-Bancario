import { Pipe, PipeTransform } from '@angular/core';

import {
  DateInput,
  formatAppDate,
} from '../utils/date-time.util';

@Pipe({
  name: 'brDate',
  standalone: true,
})
export class BrDatePipe implements PipeTransform {
  transform(value: DateInput): string {
    return formatAppDate(value);
  }
}
