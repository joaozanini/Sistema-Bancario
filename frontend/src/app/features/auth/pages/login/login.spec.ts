import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { throwError } from 'rxjs';

import { AuthService } from '../../../../core/services/auth';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let component: Login;
  let auth: { login: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    auth = { login: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    component.email = 'cli1@bantads.com.br';
    component.senha = 'errada';
  });

  function falharCom(erro: HttpErrorResponse) {
    auth.login.mockReturnValue(throwError(() => erro));
    component.entrar();
  }

  it('mostra a mensagem do Gateway no 401', () => {
    falharCom(new HttpErrorResponse({ status: 401, error: { auth: false, message: 'Login inválido!' } }));

    expect(component.erro).toBe('Login inválido!');
    expect(component.carregando).toBe(false);
  });

  it('avisa quando o servidor está fora', () => {
    falharCom(new HttpErrorResponse({ status: 0 }));

    expect(component.erro).toBe('Não foi possível conectar ao servidor. Tente novamente.');
  });

  it('usa a mensagem genérica nos outros erros', () => {
    falharCom(new HttpErrorResponse({ status: 502 }));

    expect(component.erro).toBe('Não foi possível realizar o login.');
  });
});
