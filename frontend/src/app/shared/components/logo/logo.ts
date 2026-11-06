import { Component, input } from '@angular/core';

import { Icone } from '../icone/icone';

@Component({
  selector: 'app-logo',
  imports: [Icone],
  templateUrl: './logo.html',
  styleUrl: './logo.scss',
  host: {
    '[class.claro]': "tema() === 'claro'",
  },
})
export class Logo {
  readonly tema = input<'claro' | 'escuro'>('escuro');
}
