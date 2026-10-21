import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';

import { CpfPipe } from '../../../../shared/pipes/cpf.pipe';
import { GerenteDto } from '../../models/gerente.model';
import { GerentesService } from '../../services/gerentes';

@Component({
  selector: 'app-gerentes',
  imports: [RouterLink, CpfPipe],
  templateUrl: './gerentes.html',
  styleUrl: './gerentes.scss',
})
export class Gerentes implements OnInit {
  private gerentesService = inject(GerentesService);
  private destroyRef = inject(DestroyRef);

  readonly gerentes = signal<GerenteDto[]>([]);
  readonly carregando = signal(false);
  readonly erro = signal('');

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set('');

    this.gerentesService
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lista) => {
          this.gerentes.set(lista);
          this.carregando.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.erro.set(
            err.status === 0
              ? 'Não foi possível conectar ao servidor. Tente novamente.'
              : 'Não foi possível carregar os gerentes.',
          );
          this.carregando.set(false);
        },
      });
  }
}
