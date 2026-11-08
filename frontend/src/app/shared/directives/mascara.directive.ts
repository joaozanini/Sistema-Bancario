import { Directive, ElementRef, forwardRef, inject, input } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export type TipoMascara = 'cpf' | 'cep' | 'telefone' | 'moeda';

const MAX_DIGITOS_MOEDA = 13;

export interface ValorMoeda {
  exibicao: string;
  valor: string;
}

export function formatarMoeda(digitosInformados: string): ValorMoeda {
  const digitos = digitosInformados.replace(/\D/g, '').replace(/^0+/, '').slice(0, MAX_DIGITOS_MOEDA);
  if (!digitos) {
    return { exibicao: '', valor: '' };
  }
  const completo = digitos.padStart(3, '0');
  const inteiro = completo.slice(0, -2);
  const centavos = completo.slice(-2);
  const milhares = inteiro.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return { exibicao: `${milhares},${centavos}`, valor: `${inteiro}.${centavos}` };
}

function moedaDoModelo(valor: unknown): ValorMoeda {
  if (valor === null || valor === undefined || valor === '') {
    return { exibicao: '', valor: '' };
  }
  const texto = typeof valor === 'number' ? valor.toFixed(2) : String(valor).trim();
  const partes = texto.match(/^(\d+)(?:[.,](\d{1,2}))?$/);
  if (!partes) {
    return formatarMoeda(texto);
  }
  return formatarMoeda(`${partes[1]}${(partes[2] ?? '').padEnd(2, '0')}`);
}

export function aplicarMascara(valor: string | null | undefined, tipo: TipoMascara): string {
  const digitos = (valor ?? '').replace(/\D/g, '');

  if (tipo === 'moeda') {
    return formatarMoeda(digitos).exibicao;
  }

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

  writeValue(valor: unknown): void {
    this.elemento.nativeElement.value =
      this.appMascara() === 'moeda'
        ? moedaDoModelo(valor).exibicao
        : aplicarMascara(valor === null || valor === undefined ? '' : String(valor), this.appMascara());
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

    if (this.appMascara() === 'moeda') {
      const { exibicao, valor } = formatarMoeda(campo.value);
      campo.value = exibicao;
      this.aoMudar(valor);
      return;
    }

    const formatado = aplicarMascara(campo.value, this.appMascara());
    campo.value = formatado;
    this.aoMudar(formatado);
  }

  aoTocar(): void {
    this.aoTocarCampo();
  }
}
