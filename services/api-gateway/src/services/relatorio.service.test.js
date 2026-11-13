const test = require('node:test');
const assert = require('node:assert/strict');

const contaService = require('./conta.service');
const clienteService = require('./cliente.service');
const gerenteService = require('./gerente.service');
const relatorioService = require('./relatorio.service');

const CONTA = {
  numero: '1234',
  cpfCliente: '12912861012',
  cpfGerente: '98574307084',
  saldo: '1500.0000',
};

const CLIENTE = {
  cpf: '12912861012',
  nome: 'Catharyna',
  email: 'catharyna@bantads.com.br',
  salario: '10000.00',
  telefone: '41999999999',
  endereco: { cidade: 'Curitiba', uf: 'PR' },
};

function comDublos({ contas = [CONTA], clientes = {}, gerentes = {} } = {}) {
  const originais = {
    listar: contaService.listar,
    buscarCliente: clienteService.buscar,
    buscarGerente: gerenteService.buscar,
  };
  const buscas = [];

  contaService.listar = async () => {
    if (typeof contas === 'function') return contas();
    return contas;
  };
  clienteService.buscar = async (cpf) => {
    buscas.push({ servico: 'cliente', cpf });
    return clientes[cpf] ?? { ...CLIENTE, cpf };
  };
  gerenteService.buscar = async (cpf) => {
    buscas.push({ servico: 'gerente', cpf });
    return gerentes[cpf] ?? { cpf, nome: 'Gerente Um' };
  };

  return {
    buscas,
    restaurar() {
      contaService.listar = originais.listar;
      clienteService.buscar = originais.buscarCliente;
      gerenteService.buscar = originais.buscarGerente;
    },
  };
}

test('compoe os oito campos do R16 a partir dos tres servicos', async () => {
  const d = comDublos();
  try {
    const [linha] = await relatorioService.gerar();

    assert.deepEqual(Object.keys(linha).sort(), [
      'cpf',
      'cpfGerente',
      'email',
      'nome',
      'nomeGerente',
      'numeroConta',
      'salario',
      'saldo',
    ]);
    assert.equal(linha.cpf, '12912861012');
    assert.equal(linha.nome, 'Catharyna');
    assert.equal(linha.email, 'catharyna@bantads.com.br');
    assert.equal(linha.salario, '10000.00');
    assert.equal(linha.numeroConta, '1234');
    assert.equal(linha.saldo, '1500.0000');
    assert.equal(linha.cpfGerente, '98574307084');
    assert.equal(linha.nomeGerente, 'Gerente Um');
  } finally {
    d.restaurar();
  }
});

test('ordena crescente por nome do cliente, respeitando acento', async () => {
  const contas = ['1', '2', '3'].map((n) => ({ ...CONTA, numero: n, cpfCliente: n }));
  const d = comDublos({
    contas,
    clientes: {
      1: { ...CLIENTE, cpf: '1', nome: 'Bruno' },
      2: { ...CLIENTE, cpf: '2', nome: 'Ávila' },
      3: { ...CLIENTE, cpf: '3', nome: 'Ana' },
    },
  });

  try {
    const nomes = (await relatorioService.gerar()).map((l) => l.nome);
    assert.deepEqual(nomes, ['Ana', 'Ávila', 'Bruno']);
  } finally {
    d.restaurar();
  }
});

test('salario e saldo trafegam como string (nunca float)', async () => {
  const d = comDublos();
  try {
    const [linha] = await relatorioService.gerar();
    assert.equal(typeof linha.salario, 'string');
    assert.equal(typeof linha.saldo, 'string');
  } finally {
    d.restaurar();
  }
});

test('conta sem gerente nao derruba o relatorio', async () => {
  const d = comDublos({ contas: [{ ...CONTA, cpfGerente: null }] });
  try {
    const [linha] = await relatorioService.gerar();

    assert.equal(linha.cpfGerente, null);
    assert.equal(linha.nomeGerente, null);
    assert.equal(
      d.buscas.filter((b) => b.servico === 'gerente').length,
      0,
      'sem CPF de gerente não se consulta o MS Gerente',
    );
  } finally {
    d.restaurar();
  }
});

test('falha do MS Conta propaga para quem chamou', async () => {
  const d = comDublos({
    contas: () => {
      throw new Error('MS Conta respondeu 500');
    },
  });

  try {
    await assert.rejects(() => relatorioService.gerar(), /MS Conta respondeu 500/);
  } finally {
    d.restaurar();
  }
});
