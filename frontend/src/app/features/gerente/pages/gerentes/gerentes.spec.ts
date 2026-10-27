import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { GerentesService } from '../../services/gerentes';
import { Gerentes } from './gerentes';

const GERENTES = [
  { cpf: '98574307084', nome: 'Geniéve', email: 'ger1@bantads.com.br', telefone: null, ativo: true },
  { cpf: '64065268052', nome: 'Godophredo', email: 'ger2@bantads.com.br', telefone: '(41) 99999-0002', ativo: true },
];

describe('Gerentes', () => {
  let fixture: ComponentFixture<Gerentes>;
  let gerentesService: { listar: ReturnType<typeof vi.fn> };

  async function criar() {
    await TestBed.configureTestingModule({
      imports: [Gerentes],
      providers: [provideRouter([]), { provide: GerentesService, useValue: gerentesService }],
    }).compileComponents();

    fixture = TestBed.createComponent(Gerentes);
    fixture.detectChanges();
  }

  it('lista os gerentes com link para a edição', async () => {
    gerentesService = { listar: vi.fn().mockReturnValue(of(GERENTES)) };
    await criar();

    const links = fixture.nativeElement.querySelectorAll('a.btn');
    expect(links.length).toBe(2);
    expect(links[0].getAttribute('href')).toBe('/gerente/gerentes/98574307084/editar');
  });

  it('mostra erro quando a listagem falha', async () => {
    gerentesService = {
      listar: vi.fn().mockReturnValue(throwError(() => new HttpErrorResponse({ status: 502 }))),
    };
    await criar();

    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar os gerentes.');
  });
});
