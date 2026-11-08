import { Component, input } from '@angular/core';
import Decimal from 'decimal.js';

import { BrlCurrencyPipe } from '../../pipes/brl-currency.pipe';
import { Icone } from '../icone/icone';

@Component({
  selector: 'app-saldo-disponivel',
  imports: [BrlCurrencyPipe, Icone],
  templateUrl: './saldo-disponivel.html',
  styleUrl: './saldo-disponivel.scss',
})
export class SaldoDisponivel {
  readonly saldo = input<Decimal | null>(null);
  readonly carregando = input(false);
}
