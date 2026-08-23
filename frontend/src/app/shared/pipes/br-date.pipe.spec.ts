import { BrDatePipe } from './br-date.pipe';

describe('BrDatePipe', () => {
  const pipe = new BrDatePipe();

  it('deve formatar uma data ISO', () => {
    expect(pipe.transform('2026-08-16')).toBe('16/08/2026');
  });

  it('deve converter uma data e hora UTC para a data em São Paulo', () => {
    expect(pipe.transform('2026-08-17T01:30:00Z')).toBe('16/08/2026');
  });

  it('deve representar data ausente', () => {
    expect(pipe.transform(null)).toBe('—');
  });

  it('deve representar data inválida', () => {
    expect(pipe.transform('data inválida')).toBe('—');
  });
});
