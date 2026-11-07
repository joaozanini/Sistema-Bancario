import { Component, computed, input } from '@angular/core';

import { ICONES, NomeIcone } from './icones';

@Component({
  selector: 'app-icone',
  templateUrl: './icone.html',
  styleUrl: './icone.scss',
})
export class Icone {
  readonly nome = input.required<NomeIcone>();
  readonly tamanho = input(20);

  readonly formas = computed(() => ICONES[this.nome()]);
}
