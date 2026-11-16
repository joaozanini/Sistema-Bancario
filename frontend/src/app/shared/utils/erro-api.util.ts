import { HttpErrorResponse } from '@angular/common/http';

/** Texto de erro devolvido pelo API Gateway, ou a mensagem padrão */
export function mensagemDaApi(err: HttpErrorResponse, padrao: string): string {
  const corpo = err.error as { mensagem?: unknown; message?: unknown } | null;
  const texto = corpo?.mensagem ?? corpo?.message;

  return typeof texto === 'string' && texto.trim() ? texto : padrao;
}
