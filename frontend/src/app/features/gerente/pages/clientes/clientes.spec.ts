import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { MoneyDecimal } from '../../../../shared/utils/money.util';
import { Cliente } from '../../models/cliente.model';
import { ClientesService } from '../../services/clientes';
import { Clientes, filtrarClientes } from './clientes';

const CLIENTES: Cliente[] = [
  { cpf: '12912861012', nome: 'Catharyna', cidade: 'Curitiba', estado: 'PR', saldo: new MoneyDecimal('800') },
  { cpf: '09506382000', nome: 'Cleuddônio', cidade: 'Curitiba', estado: 'PR', saldo: new MoneyDecimal('-10000') },
  { cpf: '85733854057', nome: 'Catianna', cidade: 'Curitiba', estado: 'PR', saldo: new MoneyDecimal('200') },
];

describe('filtrarClientes', () => {
  it('sem busca devolve todos', () => {
    expect(filtrarClientes(CLIENTES, '  ').length).toBe(3);
  });

  it('busca parte do nome sem diferenciar acento e maiúscula', () => {
    expect(filtrarClientes(CLIENTES, 'cleuddonio').map((c) => c.nome)).toEqual(['Cleuddônio']);
    expect(filtrarClientes(CLIENTES, 'CAT').map((c) => c.nome)).toEqual(['Catharyna', 'Catianna']);
  });

  it('busca parte do CPF, com ou sem pontuação', () => {
    expect(filtrarClientes(CLIENTES, '129.128').map((c) => c.nome)).toEqual(['Catharyna']);
    expect(filtrarClientes(CLIENTES, '0950').map((c) => c.nome)).toEqual(['Cleuddônio']);
  });

  it('dígito dentro de um nome não casa com o CPF dos outros clientes', () => {
    const comNumero: Cliente[] = [
      ...CLIENTES,
      { cpf: '00000000002', nome: 'Cliente Novo 1', cidade: 'Curitiba', estado: 'PR', saldo: new MoneyDecimal('0') },
    ];

    expect(filtrarClientes(comNumero, 'Cliente Novo 1').map((c) => c.nome)).toEqual(['Cliente Novo 1']);
  });
});

describe('Clientes', () => {
  let fixture: ComponentFixture<Clientes>;
  let clientesService: { listar: ReturnType<typeof vi.fn> };

  async function criar() {
    await TestBed.configureTestingModule({
      imports: [Clientes],
      providers: [{ provide: ClientesService, useValue: clientesService }],
    }).compileComponents();

    fixture = TestBed.createComponent(Clientes);
    fixture.detectChanges();
  }

  it('mostra CPF, nome, cidade, estado e saldo', async () => {
    clientesService = { listar: vi.fn().mockReturnValue(of(CLIENTES)) };
    await criar();

    const primeiraLinha = fixture.nativeElement.querySelector('tbody tr').textContent;
    expect(primeiraLinha).toContain('129.128.610-12');
    expect(primeiraLinha).toContain('Catharyna');
    expect(primeiraLinha).toContain('Curitiba');
    expect(primeiraLinha).toContain('PR');
    expect(primeiraLinha).toContain('R$ 800,00');
  });

  it('filtra a tabela pela busca', async () => {
    clientesService = { listar: vi.fn().mockReturnValue(of(CLIENTES)) };
    await criar();

    fixture.componentInstance.busca.set('catia');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('mostra erro quando a listagem falha', async () => {
    clientesService = {
      listar: vi.fn().mockReturnValue(throwError(() => new HttpErrorResponse({ status: 502 }))),
    };
    await criar();

    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar os clientes.');
  });
});
