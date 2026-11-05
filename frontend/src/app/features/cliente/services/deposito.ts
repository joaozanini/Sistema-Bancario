import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class DepositoService {
  private readonly http = inject(HttpClient);
  private readonly contasUrl = `${environment.apiUrl}/contas`;

  depositar(numeroConta: string, valor: string): Observable<unknown> {
    const url = `${this.contasUrl}/${encodeURIComponent(numeroConta)}/deposito`;
    return this.http.post<unknown>(url, { valor });
  }
}
