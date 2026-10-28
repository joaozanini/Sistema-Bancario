import { Component, inject } from '@angular/core';
import { Params, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService, TipoUsuario } from '../../core/services/auth';

export interface ItemMenu {
  rotulo: string;
  link: string;
  queryParams?: Params;
}

const MENUS: Record<TipoUsuario, ItemMenu[]> = {
  CLIENTE: [
    { rotulo: 'Início', link: '/cliente' },
    { rotulo: 'Extrato', link: '/extrato' },
    { rotulo: 'Transferência', link: '/transferencia' },
  ],
  GERENTE: [
    { rotulo: 'Início', link: '/gerente' },
    { rotulo: 'Clientes', link: '/gerente/clientes' },
    { rotulo: 'Gerentes', link: '/gerente/gerentes' },
    { rotulo: 'Relatório', link: '/gerente/relatorio' },
  ],
};

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class SidebarComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  private readonly tipo: TipoUsuario = this.auth.obterTipo() ?? tipoPelaUrl(this.router.url);

  readonly itens = MENUS[this.tipo];

  saindo = false;

  sair(evento: Event): void {
    evento.preventDefault();
    if (this.saindo) {
      return;
    }

    this.saindo = true;
    this.auth.sair().subscribe({
      complete: () => this.router.navigateByUrl('/login'),
    });
  }
}

function tipoPelaUrl(url: string): TipoUsuario {
  const segmento = url.split(/[/?#]/)[1]?.toUpperCase();
  return segmento === 'GERENTE' ? 'GERENTE' : 'CLIENTE';
}
