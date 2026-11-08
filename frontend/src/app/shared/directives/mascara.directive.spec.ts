import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';

import { aplicarMascara, MascaraDirective } from './mascara.directive';

describe('aplicarMascara', () => {
  it('formata CPF enquanto digita', () => {
    expect(aplicarMascara('119', 'cpf')).toBe('119');
    expect(aplicarMascara('1193', 'cpf')).toBe('119.3');
    expect(aplicarMascara('1193716', 'cpf')).toBe('119.371.6');
    expect(aplicarMascara('11937167992', 'cpf')).toBe('119.371.679-92');
    expect(aplicarMascara('119371679921234', 'cpf')).toBe('119.371.679-92');
  });

  it('formata CEP', () => {
    expect(aplicarMascara('81530', 'cep')).toBe('81530');
    expect(aplicarMascara('81530000', 'cep')).toBe('81530-000');
    expect(aplicarMascara('81530-0009', 'cep')).toBe('81530-000');
  });

  it('formata telefone fixo e celular', () => {
    expect(aplicarMascara('4', 'telefone')).toBe('(4');
    expect(aplicarMascara('41998', 'telefone')).toBe('(41) 998');
    expect(aplicarMascara('4133334444', 'telefone')).toBe('(41) 3333-4444');
    expect(aplicarMascara('41997123127', 'telefone')).toBe('(41) 99712-3127');
    expect(aplicarMascara('419971231273', 'telefone')).toBe('(41) 99712-3127');
  });

  it('ignora letras e símbolos', () => {
    expect(aplicarMascara('abc123.456', 'cpf')).toBe('123.456');
    expect(aplicarMascara(null, 'cep')).toBe('');
  });
});

@Component({
  imports: [FormsModule, MascaraDirective],
  template: `<input name="cpf" appMascara="cpf" [(ngModel)]="cpf">`,
})
class CampoCpf {
  cpf = '';
}

describe('MascaraDirective', () => {
  let fixture: ComponentFixture<CampoCpf>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CampoCpf] }).compileComponents();
    fixture = TestBed.createComponent(CampoCpf);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('formata o campo e atualiza o ngModel enquanto digita', () => {
    const campo = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    campo.value = '11937167992';
    campo.dispatchEvent(new Event('input'));

    expect(campo.value).toBe('119.371.679-92');
    expect(fixture.componentInstance.cpf).toBe('119.371.679-92');
  });
});
