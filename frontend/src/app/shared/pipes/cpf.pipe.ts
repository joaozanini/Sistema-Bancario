import { Pipe, PipeTransform } from '@angular/core';

/** 12345678900 -> 123.456.789-00 */
@Pipe({
  name: 'cpf',
  standalone: true,
})
export class CpfPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) {
      return '—';
    }

    const digitos = value.replace(/\D/g, '');

    if (digitos.length !== 11) {
      return value;
    }

    return digitos.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4');
  }
}
