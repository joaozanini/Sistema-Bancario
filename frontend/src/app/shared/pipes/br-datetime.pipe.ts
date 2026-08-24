import { Pipe, PipeTransform } from '@angular/core';

import {
  DateInput,
  formatAppDateTime,
} from '../utils/date-time.util';

@Pipe({
  name: 'brDateTime',
  standalone: true,
})
export class BrDateTimePipe implements PipeTransform {
  transform(value: DateInput): string {
    return formatAppDateTime(value);
  }
}
