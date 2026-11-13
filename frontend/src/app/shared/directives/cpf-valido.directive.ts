import { Directive, forwardRef } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';

import { cpfValido } from '../utils/cpf.util';

@Directive({
  selector: '[appCpfValido]',
  providers: [{ provide: NG_VALIDATORS, useExisting: forwardRef(() => CpfValidoDirective), multi: true }],
})
export class CpfValidoDirective implements Validator {
  validate(control: AbstractControl): ValidationErrors | null {
    const valor = String(control.value ?? '');
    if (valor.replace(/\D/g, '').length < 11) {
      return null;
    }
    return cpfValido(valor) ? null : { cpfInvalido: true };
  }
}
