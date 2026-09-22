export function obterCpfDoToken(token: string | null): string | null {
  if (!token) {
    return null;
  }

  try {
    const payloadBase64Url = token.split('.')[1];

    if (!payloadBase64Url) {
      return null;
    }

    const base64 = payloadBase64Url.replace(/-/g, '+').replace(/_/g, '/');
    const comPadding = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
    const payload = JSON.parse(atob(comPadding)) as { cpf?: unknown };

    return typeof payload.cpf === 'string'
      ? payload.cpf.replace(/\D/g, '')
      : null;
  } catch {
    return null;
  }
}