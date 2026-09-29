import { CpfPipe } from './cpf.pipe';

describe('CpfPipe', () => {
  const pipe = new CpfPipe();

  it('formata 11 dígitos', () => {
    expect(pipe.transform('12912861012')).toBe('129.128.610-12');
  });

  it('trata vazio e valores fora do padrão', () => {
    expect(pipe.transform(null)).toBe('—');
    expect(pipe.transform('123')).toBe('123');
  });
});
