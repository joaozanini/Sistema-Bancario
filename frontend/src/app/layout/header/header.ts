import { Component, inject } from '@angular/core';
import { AuthService, TipoUsuario } from '../../core/services/auth';

const ROTULO_TIPO: Record<TipoUsuario, string> = {
  CLIENTE: 'Cliente',
  GERENTE: 'Gerente',
  ADMIN: 'Administrador',
};

@Component({
  selector: 'app-header',
  imports: [],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class HeaderComponent {
  private readonly auth = inject(AuthService);

  readonly usuario = this.auth.obterUsuario();
  private readonly tipo = this.auth.obterTipo();

  readonly rotuloTipo = this.tipo ? ROTULO_TIPO[this.tipo] : null;

  readonly iniciais = obterIniciais(this.usuario?.nome);
}

/** "Roberto Souza" -> "RS" */
function obterIniciais(nome: string | undefined): string {
  const partes = (nome ?? '').trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) {
    return '?';
  }
  const primeira = partes[0][0];
  const ultima = partes.length > 1 ? partes[partes.length - 1][0] : '';
  return (primeira + ultima).toUpperCase();
}
