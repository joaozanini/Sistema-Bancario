import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BrlCurrencyPipe } from '../../../../shared/pipes/brl-currency.pipe';

@Component({
  selector: 'app-home',
  imports: [RouterLink, BrlCurrencyPipe],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly route = inject(ActivatedRoute);
  private readonly parametros = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });

  readonly secao = computed(() => this.parametros().get('secao') ?? 'resumo');
  readonly mostrarSaldo = signal(true);

  // Dados de demonstração enquanto o serviço de conta não oferece consulta.
  readonly conta = {
    agencia: '0001',
    numero: '000123-4',
    tipo: 'Conta corrente',
    saldo: '1250.00',
  };

  alternarSaldo(): void {
    this.mostrarSaldo.update((visivel) => !visivel);
  }
}
