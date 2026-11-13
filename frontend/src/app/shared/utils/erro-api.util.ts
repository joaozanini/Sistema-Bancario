import { HttpErrorResponse } from '@angular/common/http';

/**
 * Texto de erro devolvido pelo API Gateway. Os erros seguem o corpo padrão
 * `{ status, erro, mensagem }`; o 401 do login/token usa `{ auth, message }`.
 * Sem texto no corpo (ex.: servidor fora do ar), vale a mensagem padrão.
 */
export function mensagemDaApi(err: HttpErrorResponse, padrao: string): string {
  const corpo = err.error as { mensagem?: unknown; message?: unknown } | null;
  const texto = corpo?.mensagem ?? corpo?.message;

  return typeof texto === 'string' && texto.trim() ? texto : padrao;
}
