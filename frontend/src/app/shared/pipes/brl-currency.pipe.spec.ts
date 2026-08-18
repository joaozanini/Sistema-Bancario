import { BrlCurrencyPipe } from './brl-currency.pipe';

describe('BrlCurrencyPipe', () => {
  const pipe = new BrlCurrencyPipe();

  it('deve formatar valor em reais', () => {
    expect(pipe.transform('1234.5')).toBe('R$ 1.234,50');
  });

  it('deve formatar valor negativo', () => {
    expect(pipe.transform('-50')).toBe('-R$ 50,00');
  });

  it('deve arredondar com ROUND_HALF_UP', () => {
    expect(pipe.transform('1.005')).toBe('R$ 1,01');
  });

  it('deve representar valor ausente', () => {
    expect(pipe.transform(null)).toBe('—');
  });

  it('deve representar valor inválido', () => {
    expect(pipe.transform('valor inválido')).toBe('—');
  });
});