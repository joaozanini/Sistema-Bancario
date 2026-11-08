
import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService, TipoUsuario } from '../../core/services/auth';
import { Icone } from '../../shared/components/icone/icone';
import { NomeIcone } from '../../shared/components/icone/icones';
import { Logo } from '../../shared/components/logo/logo';

export interface ItemMenu {
  rotulo: string;
  link: string;
  icone: NomeIcone;
}

const MENUS: Record<TipoUsuario, ItemMenu[]> = {
  CLIENTE: [
    { rotulo: 'Início', link: '/cliente', icone: 'painel' },
    { rotulo: 'Depósito', link: '/deposito', icone: 'deposito' },
    { rotulo: 'Saque', link: '/saque', icone: 'saque' },
    { rotulo: 'Transferência', link: '/transferencia', icone: 'transferencia' },
    { rotulo: 'Extrato', link: '/extrato', icone: 'extrato' },
  ],
  GERENTE: [
    { rotulo: 'Início', link: '/gerente', icone: 'painel' },
    { rotulo: 'Clientes', link: '/gerente/clientes', icone: 'clientes' },
    { rotulo: 'Gerentes', link: '/gerente/gerentes', icone: 'gerentes' },
    { rotulo: 'Relatório', link: '/gerente/relatorio', icone: 'relatorio' },
  ],
};

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive, Icone, Logo],
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
