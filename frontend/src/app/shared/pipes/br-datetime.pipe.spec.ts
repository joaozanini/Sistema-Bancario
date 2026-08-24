import { BrDateTimePipe } from './br-datetime.pipe';

describe('BrDateTimePipe', () => {
  const pipe = new BrDateTimePipe();

  it('deve converter UTC para o horário de São Paulo', () => {
    expect(pipe.transform('2026-08-16T17:30:00Z')).toBe(
      '16/08/2026 14:30',
    );
  });

  it('deve preservar um horário com offset de São Paulo', () => {
    expect(pipe.transform('2026-08-16T14:30:00-03:00')).toBe(
      '16/08/2026 14:30',
    );
  });

  it('deve representar data ausente', () => {
    expect(pipe.transform(undefined)).toBe('—');
  });

  it('deve representar data inválida', () => {
    expect(pipe.transform('data inválida')).toBe('—');
  });
});
