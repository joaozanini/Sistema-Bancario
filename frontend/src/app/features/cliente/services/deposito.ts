import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class DepositoService {
  private readonly http = inject(HttpClient);
  private readonly contasUrl = 'http://localhost:3000/contas';

  depositar(numeroConta: string, valor: string): Observable<unknown> {
    const url = `${this.contasUrl}/${encodeURIComponent(numeroConta)}/deposito`;
    return this.http.post<unknown>(url, { valor });
  }
}
