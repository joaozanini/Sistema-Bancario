const test = require('node:test');
const assert = require('node:assert/strict');

const { listarTodos } = require('./clientes.service');
const { ClienteNaoEncontradoError } = require('./cliente.service');

const json = (corpo, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => corpo,
});

function comMicrosservicos({ contas, clientes = {} }) {
  const original = globalThis.fetch;
  const urls = [];

  globalThis.fetch = async (url) => {
    urls.push(url);
    if (url.endsWith('/contas')) return contas;
    const cpf = url.split('/clientes/')[1];
    return clientes[cpf] ? json(clientes[cpf]) : json({}, 404);
  };

  return {
    urls,
    restaurar() {
      globalThis.fetch = original;
    },
  };
}

const conta = (cpfCliente, saldo) => ({ numeroConta: cpfCliente.slice(0, 4), cpfCliente, saldo });
const cliente = (cpf, nome) => ({ cpf, nome, email: `${cpf}@bantads.com.br`, cidade: 'Curitiba', uf: 'PR' });

test('junta o saldo da conta aos dados do cliente', async () => {
  const ms = comMicrosservicos({
    contas: json([conta('12912861012', '800.00')]),
    clientes: { 12912861012: cliente('12912861012', 'Catharyna') },
  });

  try {
    assert.deepEqual(await listarTodos(), [
      { cpf: '12912861012', nome: 'Catharyna', cidade: 'Curitiba', uf: 'PR', saldo: '800.00', _links: undefined },
    ]);
  } finally {
    ms.restaurar();
  }
});

test('ordena por nome sem deixar os acentuados para o fim', async () => {
  const ms = comMicrosservicos({
    contas: json([conta('111', '1.00'), conta('222', '2.00'), conta('333', '3.00')]),
    clientes: {
      111: cliente('111', 'Zuleica'),
      222: cliente('222', 'Érica'),
      333: cliente('333', 'Catianna'),
    },
  });

  try {
    const nomes = (await listarTodos()).map((c) => c.nome);
    assert.deepEqual(nomes, ['Catianna', 'Érica', 'Zuleica']);
  } finally {
    ms.restaurar();
  }
});

test('repassa os _links criados pelo MS Cliente', async () => {
  const _links = { self: { href: 'http://ms-cliente/clientes/111' } };
  const ms = comMicrosservicos({
    contas: json([conta('111', '1.00')]),
    clientes: { 111: { ...cliente('111', 'Catianna'), _links } },
  });

  try {
    const [resultado] = await listarTodos();
    assert.deepEqual(resultado._links, _links);
  } finally {
    ms.restaurar();
  }
});

test('sem contas devolve lista vazia e nao consulta o MS Cliente', async () => {
  const ms = comMicrosservicos({ contas: json([]) });

  try {
    assert.deepEqual(await listarTodos(), []);
    assert.equal(ms.urls.length, 1);
  } finally {
    ms.restaurar();
  }
});

test('conta de cliente inexistente derruba a consulta em vez de sumir da lista', async () => {
  const ms = comMicrosservicos({ contas: json([conta('999', '1.00')]) });

  try {
    await assert.rejects(() => listarTodos(), ClienteNaoEncontradoError);
  } finally {
    ms.restaurar();
  }
});

test('erro do MS Conta e propagado', async () => {
  const ms = comMicrosservicos({ contas: json({}, 500) });

  try {
    await assert.rejects(() => listarTodos(), /MS Conta respondeu 500/);
  } finally {
    ms.restaurar();
  }
});
