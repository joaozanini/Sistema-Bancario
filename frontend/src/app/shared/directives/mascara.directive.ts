import { Directive, ElementRef, forwardRef, inject, input } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export type TipoMascara = 'cpf' | 'cep' | 'telefone';

export function aplicarMascara(valor: string | null | undefined, tipo: TipoMascara): string {
  const digitos = (valor ?? '').replace(/\D/g, '');

  if (tipo === 'cpf') {
    const d = digitos.slice(0, 11);
    if (d.length <= 3) {
      return d;
    }
    if (d.length <= 6) {
      return `${d.slice(0, 3)}.${d.slice(3)}`;
    }
    if (d.length <= 9) {
      return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6)}`;
    }
    return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`;
  }

  if (tipo === 'cep') {
    const d = digitos.slice(0, 8);
    return d.length <= 5 ? d : `${d.slice(0, 5)}-${d.slice(5)}`;
  }

  const d = digitos.slice(0, 11);
  if (d.length === 0) {
    return '';
  }
  if (d.length <= 2) {
    return `(${d}`;
  }
  const ddd = d.slice(0, 2);
  const numero = d.slice(2);
  if (numero.length <= 4) {
    return `(${ddd}) ${numero}`;
  }
  const corte = d.length === 11 ? 5 : 4;
  return `(${ddd}) ${numero.slice(0, corte)}-${numero.slice(corte)}`;
}

@Directive({
  selector: 'input[appMascara]',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MascaraDirective),
      multi: true,
    },
  ],
  host: {
    '(input)': 'aoDigitar()',
    '(blur)': 'aoTocar()',
  },
})
export class MascaraDirective implements ControlValueAccessor {
  readonly appMascara = input.required<TipoMascara>();

  private readonly elemento = inject<ElementRef<HTMLInputElement>>(ElementRef);
  private aoMudar: (valor: string) => void = () => undefined;
  private aoTocarCampo: () => void = () => undefined;

  writeValue(valor: string | null): void {
    this.elemento.nativeElement.value = aplicarMascara(valor, this.appMascara());
  }

  registerOnChange(fn: (valor: string) => void): void {
    this.aoMudar = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.aoTocarCampo = fn;
  }

  setDisabledState(desabilitado: boolean): void {
    this.elemento.nativeElement.disabled = desabilitado;
  }

  aoDigitar(): void {
    const campo = this.elemento.nativeElement;
    const formatado = aplicarMascara(campo.value, this.appMascara());
    campo.value = formatado;
    this.aoMudar(formatado);
  }

  aoTocar(): void {
    this.aoTocarCampo();
  }
}
