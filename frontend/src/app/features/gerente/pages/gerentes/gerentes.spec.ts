import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../../core/services/auth';
import { GerentesService } from '../../services/gerentes';
import { Gerentes } from './gerentes';

const GERENTES = [
  { cpf: '98574307084', nome: 'Geniéve', email: 'ger1@bantads.com.br', telefone: null, ativo: true, quantidadeClientes: 2 },
  { cpf: '64065268052', nome: 'Godophredo', email: 'ger2@bantads.com.br', telefone: '(41) 99999-0002', ativo: true, quantidadeClientes: 1 },
];

describe('Gerentes', () => {
  let fixture: ComponentFixture<Gerentes>;
  let component: Gerentes;
  let gerentesService: { listar: ReturnType<typeof vi.fn>; remover: ReturnType<typeof vi.fn> };

  async function criar() {
    await TestBed.configureTestingModule({
      imports: [Gerentes],
      providers: [
        provideRouter([]),
        { provide: GerentesService, useValue: gerentesService },
        // logado como Geniéve
        { provide: AuthService, useValue: { obterUsuario: () => ({ cpf: '98574307084' }) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Gerentes);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    gerentesService = {
      listar: vi.fn().mockReturnValue(of(GERENTES)),
      remover: vi.fn().mockReturnValue(of('Gerente removido com sucesso.')),
    };
  });

  it('lista os gerentes com a quantidade de clientes e link para a edição', async () => {
    await criar();

    const links = fixture.nativeElement.querySelectorAll('a.btn');
    expect(links.length).toBe(2);
    expect(links[0].getAttribute('href')).toBe('/gerente/gerentes/98574307084/editar');
    expect(fixture.nativeElement.querySelector('tbody td.numero').textContent.trim()).toBe('2');
  });

  it('mostra erro quando a listagem falha', async () => {
    gerentesService.listar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 502 })));
    await criar();

    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar os gerentes.');
  });

  it('não mostra Remover na linha do próprio gerente logado', async () => {
    await criar();

    const botoes = fixture.nativeElement.querySelectorAll('button.remover');
    expect(botoes.length).toBe(1);
  });

  it('remove após confirmar, mostra a mensagem e recarrega a lista', async () => {
    await criar();

    component.abrirRemocao(GERENTES[1]);
    component.confirmarRemocao();
    fixture.detectChanges();

    expect(gerentesService.remover).toHaveBeenCalledWith('64065268052');
    expect(component.emRemocao()).toBeNull();
    expect(component.mensagem()).toBe('Gerente removido com sucesso.');
    expect(gerentesService.listar).toHaveBeenCalledTimes(2);
  });

  it('mostra o 403 do Gateway', async () => {
    gerentesService.remover.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 403, error: { message: 'Um gerente não pode remover a si mesmo.' } }),
      ),
    );
    await criar();

    component.abrirRemocao(GERENTES[1]);
    component.confirmarRemocao();

    expect(component.erroRemocao()).toBe('Um gerente não pode remover a si mesmo.');
    expect(component.emRemocao()).not.toBeNull();
  });

  it('mostra o erro do job em FALHA', async () => {
    gerentesService.remover.mockReturnValue(
      throwError(() => new Error('Não é permitido remover o último gerente ativo.')),
    );
    await criar();

    component.abrirRemocao(GERENTES[1]);
    component.confirmarRemocao();

    expect(component.erroRemocao()).toBe('Não é permitido remover o último gerente ativo.');
  });
});
