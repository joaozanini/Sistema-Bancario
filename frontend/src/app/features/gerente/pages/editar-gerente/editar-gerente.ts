import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { CpfPipe } from '../../../../shared/pipes/cpf.pipe';
import { GerenteDto } from '../../models/gerente.model';
import { GerentesService } from '../../services/gerentes';

export const ROTA_LISTAGEM_GERENTES = '/gerente/gerentes';

@Component({
  selector: 'app-editar-gerente',
  imports: [FormsModule, RouterLink, CpfPipe],
  templateUrl: './editar-gerente.html',
  styleUrl: './editar-gerente.scss',
})
export class EditarGerente implements OnInit {
  private gerentesService = inject(GerentesService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  readonly rotaListagem = ROTA_LISTAGEM_GERENTES;

  readonly gerente = signal<GerenteDto | null>(null);
  readonly carregando = signal(false);
  readonly erroCarregamento = signal('');
  readonly salvando = signal(false);
  readonly erro = signal('');

  nome = '';
  telefone = '';

  private readonly cpf = this.route.snapshot.paramMap.get('cpf') ?? '';

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erroCarregamento.set('');

    this.gerentesService
      .buscar(this.cpf)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (gerente) => {
          this.gerente.set(gerente);
          this.nome = gerente.nome;
          this.telefone = gerente.telefone ?? '';
          this.carregando.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.erroCarregamento.set(this.mensagemDeErro(err, 'Não foi possível carregar o gerente.'));
          this.carregando.set(false);
        },
      });
  }

  salvar(form: NgForm): void {
    this.erro.set('');

    if (form.invalid) {
      form.control.markAllAsTouched();
      return;
    }

    this.salvando.set(true);

    this.gerentesService
      .atualizar(this.cpf, { nome: this.nome.trim(), telefone: this.telefone.trim() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.salvando.set(false);
          this.router.navigateByUrl(ROTA_LISTAGEM_GERENTES);
        },
        error: (err: HttpErrorResponse) => {
          this.salvando.set(false);
          this.erro.set(this.mensagemDeErro(err, 'Não foi possível salvar as alterações.'));
        },
      });
  }

  // o MS Gerente devolve { erro }: 400 sem nome, 404 se o gerente não existe
  private mensagemDeErro(err: HttpErrorResponse, padrao: string): string {
    if (err.status === 0) {
      return 'Não foi possível conectar ao servidor. Tente novamente.';
    }
    if (err.status === 404) {
      return err.error?.erro || 'Gerente não encontrado.';
    }
    return err.error?.erro || err.error?.message || padrao;
  }
}
