import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AutocadastroService } from '../../services/autocadastro';
import { CepNaoEncontradoError, CepService } from '../../services/cep';
import { MENSAGEM_CADASTRO_ENVIADO, Register } from './register';

describe('Register', () => {
  let fixture: ComponentFixture<Register>;
  let component: Register;
  let autocadastro: { solicitar: ReturnType<typeof vi.fn> };
  let cepService: { buscar: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    autocadastro = { solicitar: vi.fn().mockReturnValue(of({})) };
    cepService = {
      buscar: vi.fn().mockReturnValue(
        of({ logradouro: 'Rua XV de Novembro', complemento: '', cidade: 'Curitiba', uf: 'PR' }),
      ),
    };

    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [
        provideRouter([]),
        { provide: AutocadastroService, useValue: autocadastro },
        { provide: CepService, useValue: cepService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  async function preencher(cpf = '129.128.610-12') {
    component.nome = 'Catharyna';
    component.cpf = cpf;
    component.telefone = '(41) 99999-0001';
    component.email = 'cli1@bantads.com.br';
    component.salario = '10000.00';
    component.logradouro = 'Rua XV de Novembro';
    component.numero = '100';
    component.cep = '80060-000';
    component.cidade = 'Curitiba';
    component.estado = 'PR';
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function enviar() {
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('não envia com CPF de dígito verificador inválido e mostra o erro', async () => {
    await preencher('129.128.610-13');
    enviar();

    expect(autocadastro.solicitar).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('CPF inválido');
  });

  it('preenche logradouro, cidade e estado a partir do CEP', async () => {
    component.aoAlterarCep('80060-000');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(cepService.buscar).toHaveBeenCalledWith('80060000');
    expect(component.logradouro).toBe('Rua XV de Novembro');
    expect(component.cidade).toBe('Curitiba');
    expect(component.estado).toBe('PR');
  });

  it('não busca o CEP enquanto não tiver 8 dígitos, nem repete a mesma busca', () => {
    component.aoAlterarCep('80060-0');
    component.aoAlterarCep('80060-000');
    component.aoAlterarCep('80060-000');

    expect(cepService.buscar).toHaveBeenCalledTimes(1);
  });

  it('avisa quando o CEP não existe', () => {
    cepService.buscar.mockReturnValue(throwError(() => new CepNaoEncontradoError()));

    component.aoAlterarCep('99999-999');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('CEP não encontrado');
  });

  it('mostra a mensagem do back quando o CPF ou e-mail já foi usado', async () => {
    autocadastro.solicitar.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { erro: 'E-mail já utilizado em outra solicitação.' },
          }),
      ),
    );
    await preencher();
    enviar();

    expect(component.enviando()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('E-mail já utilizado em outra solicitação.');
  });

  it('no sucesso redireciona para o login com a mensagem de confirmação', async () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await preencher();
    enviar();

    expect(autocadastro.solicitar).toHaveBeenCalledWith(
      expect.objectContaining({ cpf: '12912861012', uf: 'PR', salario: '10000.00' }),
    );
    expect(navegar).toHaveBeenCalledWith('/login', {
      state: { mensagem: MENSAGEM_CADASTRO_ENVIADO },
    });
  });
});
