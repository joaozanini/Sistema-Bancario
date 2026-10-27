import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { AtualizacaoGerente, GerenteDto } from '../models/gerente.model';

@Injectable({
  providedIn: 'root',
})
export class GerentesService {
  private http = inject(HttpClient);

  private readonly gerentesUrl = 'http://localhost:3000/gerentes';

  // R12: ordenada por nome
  listar(): Observable<GerenteDto[]> {
    return this.http
      .get<GerenteDto[]>(this.gerentesUrl)
      .pipe(map((gerentes) => [...gerentes].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))));
  }

  buscar(cpf: string): Observable<GerenteDto> {
    return this.http.get<GerenteDto>(`${this.gerentesUrl}/${cpf}`);
  }

  atualizar(cpf: string, dados: AtualizacaoGerente): Observable<GerenteDto> {
    return this.http.put<GerenteDto>(`${this.gerentesUrl}/${cpf}`, dados);
  }
}
