import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { GerentesService } from '../../services/gerentes';
import { NovoGerente } from './novo-gerente';

describe('NovoGerente', () => {
  let fixture: ComponentFixture<NovoGerente>;
  let component: NovoGerente;
  let gerentesService: { inserir: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    gerentesService = {
      inserir: vi.fn().mockReturnValue(of('Gerente Gertrudes inserido com sucesso.')),
    };

    await TestBed.configureTestingModule({
      imports: [NovoGerente],
      providers: [provideRouter([]), { provide: GerentesService, useValue: gerentesService }],
    }).compileComponents();

    fixture = TestBed.createComponent(NovoGerente);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  async function preencher() {
    component.nome = ' Gertrudes ';
    component.cpf = '111.222.333-44';
    component.email = 'ger5@bantads.com.br';
    component.telefone = '(41) 99999-0005';
    component.senha = 'tads';
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function enviar() {
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('não envia com campos obrigatórios vazios', () => {
    enviar();

    expect(gerentesService.inserir).not.toHaveBeenCalled();
  });

  it('envia o CPF só com dígitos e mostra a mensagem de sucesso', async () => {
    await preencher();
    enviar();

    expect(gerentesService.inserir).toHaveBeenCalledWith({
      nome: 'Gertrudes',
      cpf: '11122233344',
      email: 'ger5@bantads.com.br',
      telefone: '(41) 99999-0005',
      senha: 'tads',
    });
    expect(fixture.nativeElement.textContent).toContain('Gerente Gertrudes inserido com sucesso.');
  });

  it('mostra o erro do job em FALHA', async () => {
    gerentesService.inserir.mockReturnValue(throwError(() => new Error('E-mail já cadastrado')));
    await preencher();
    enviar();

    expect(component.erro()).toBe('E-mail já cadastrado');
  });

  it('mostra o erro de validação do Gateway', async () => {
    gerentesService.inserir.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 400, error: { message: 'Campos obrigatórios: senha.' } }),
      ),
    );
    await preencher();
    enviar();

    expect(component.erro()).toBe('Campos obrigatórios: senha.');
  });
});
