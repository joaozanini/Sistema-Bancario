import { Component, input } from '@angular/core';

@Component({
  selector: 'app-logo',
  templateUrl: './logo.html',
  styleUrl: './logo.scss',
  host: {
    '[class.claro]': "tema() === 'claro'",
  },
})
export class Logo {
  readonly tema = input<'claro' | 'escuro'>('escuro');
}
