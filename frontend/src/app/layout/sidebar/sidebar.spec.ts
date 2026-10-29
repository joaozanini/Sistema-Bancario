import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/services/auth';
import { SidebarComponent } from './sidebar';

describe('Sidebar', () => {
  let component: SidebarComponent;
  let fixture: ComponentFixture<SidebarComponent>;
  let auth: { obterTipo: ReturnType<typeof vi.fn>; sair: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    auth = {
      obterTipo: vi.fn().mockReturnValue('CLIENTE'),
      sair: vi.fn().mockReturnValue(of(undefined)),
    };

    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();

    fixture = TestBed.createComponent(SidebarComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('leva o Extrato do cliente para a tela de extrato', () => {
    expect(component.itens.find((i) => i.rotulo === 'Extrato')?.link).toBe('/extrato');
  });

  it('Sair encerra a sessão no Gateway e volta para o login', () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const evento = new Event('click', { cancelable: true });

    component.sair(evento);

    expect(evento.defaultPrevented).toBe(true);
    expect(auth.sair).toHaveBeenCalledOnce();
    expect(navegar).toHaveBeenCalledWith('/login');
  });
});
