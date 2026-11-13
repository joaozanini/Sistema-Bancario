import { cpfValido } from './cpf.util';

describe('cpfValido', () => {
  it('aceita CPFs com dígitos verificadores corretos, com ou sem pontuação', () => {
    expect(cpfValido('12912861012')).toBe(true);
    expect(cpfValido('985.743.070-84')).toBe(true);
    expect(cpfValido('111.444.777-35')).toBe(true);
  });

  it('recusa dígito verificador errado', () => {
    expect(cpfValido('12912861013')).toBe(false);
    expect(cpfValido('111.444.777-53')).toBe(false);
  });

  it('recusa sequências repetidas e tamanho diferente de 11', () => {
    expect(cpfValido('111.111.111-11')).toBe(false);
    expect(cpfValido('00000000000')).toBe(false);
    expect(cpfValido('1291286101')).toBe(false);
    expect(cpfValido('')).toBe(false);
    expect(cpfValido(null)).toBe(false);
  });
});
