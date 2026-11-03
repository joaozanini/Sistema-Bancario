import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { GerentesService } from '../../services/gerentes';
import { EditarGerente, ROTA_LISTAGEM_GERENTES } from './editar-gerente';

const GERENTE = {
  cpf: '98574307084',
  nome: 'Geniéve',
  email: 'ger1@bantads.com.br',
  telefone: '(41) 99999-0001',
  ativo: true,
};

describe('EditarGerente', () => {
  let fixture: ComponentFixture<EditarGerente>;
  let component: EditarGerente;
  let gerentesService: {
    buscar: ReturnType<typeof vi.fn>;
    atualizar: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    gerentesService = {
      buscar: vi.fn().mockReturnValue(of(GERENTE)),
      atualizar: vi.fn().mockReturnValue(of(GERENTE)),
    };

    await TestBed.configureTestingModule({
      imports: [EditarGerente],
      providers: [
        provideRouter([]),
        { provide: GerentesService, useValue: gerentesService },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ cpf: GERENTE.cpf }) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EditarGerente);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('carrega o gerente da rota e preenche nome e telefone', () => {
    expect(gerentesService.buscar).toHaveBeenCalledWith(GERENTE.cpf);
    expect(component.nome).toBe(GERENTE.nome);
    expect(component.telefone).toBe(GERENTE.telefone);
  });

  it('deixa CPF e e-mail somente leitura', () => {
    const readonly = fixture.nativeElement.querySelectorAll('input[readonly]');
    expect(readonly.length).toBe(2);
  });

  it('envia só nome e telefone e volta para a listagem', () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    component.nome = '  Novo Nome ';

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    expect(gerentesService.atualizar).toHaveBeenCalledWith(GERENTE.cpf, {
      nome: 'Novo Nome',
      telefone: GERENTE.telefone,
    });
    expect(navegar).toHaveBeenCalledWith(ROTA_LISTAGEM_GERENTES);
  });

  it('mostra o erro devolvido pelo back', () => {
    gerentesService.atualizar.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { erro: 'Nome é obrigatório.' } })),
    );

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(component.erro()).toBe('Nome é obrigatório.');
  });

  it('avisa quando o gerente não existe', () => {
    gerentesService.buscar.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: {} })),
    );

    component.carregar();

    expect(component.erroCarregamento()).toBe('Gerente não encontrado.');
  });
});
