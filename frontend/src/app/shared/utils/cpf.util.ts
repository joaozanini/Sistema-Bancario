export function cpfValido(cpf: string | null | undefined): boolean {
  const digitos = (cpf ?? '').replace(/\D/g, '');

  if (digitos.length !== 11 || /^(\d)\1{10}$/.test(digitos)) {
    return false;
  }

  const numeros = digitos.split('').map(Number);
  return (
    digitoVerificador(numeros.slice(0, 9)) === numeros[9] &&
    digitoVerificador(numeros.slice(0, 10)) === numeros[10]
  );
}

function digitoVerificador(base: number[]): number {
  const pesoInicial = base.length + 1;
  const soma = base.reduce((total, numero, i) => total + numero * (pesoInicial - i), 0);
  const resto = (soma * 10) % 11;
  return resto === 10 ? 0 : resto;
}
