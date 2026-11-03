import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface TransferenciaRequest {
  contaDestino: string;
  valor: string;
}

@Injectable({
  providedIn: 'root',
})
export class TransferenciaService {
  private readonly http = inject(HttpClient);
  private readonly contasUrl = `${environment.apiUrl}/contas`;

  transferir(
    numeroContaOrigem: string,
    contaDestino: string,
    valor: string,
  ): Observable<unknown> {
    const url = `${this.contasUrl}/${encodeURIComponent(numeroContaOrigem)}/transferencia`;
    const corpo: TransferenciaRequest = { contaDestino, valor };

    return this.http.post<unknown>(url, corpo);
  }
}
