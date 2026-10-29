const test = require('node:test');
const assert = require('node:assert/strict');

const { listarTodos } = require('./gerentes.service');

const json = (corpo, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => corpo,
});

function comMicrosservicos({ gerentes, contas }) {
  const original = globalThis.fetch;
  const urls = [];

  globalThis.fetch = async (url) => {
    urls.push(url);
    return url.endsWith('/gerentes') ? gerentes : contas;
  };

  return {
    urls,
    restaurar() {
      globalThis.fetch = original;
    },
  };
}

const gerente = (cpf, nome, extra = {}) => ({
  cpf,
  nome,
  email: `${cpf}@bantads.com.br`,
  telefone: '41999990000',
  ativo: true,
  ...extra,
});
const conta = (numeroConta, gerenteCpf) => ({ numeroConta, cpfCliente: `cli${numeroConta}`, gerenteCpf });

test('junta a quantidade de clientes aos dados do gerente', async () => {
  const ms = comMicrosservicos({
    gerentes: json([gerente('111', 'Geniéve')]),
    contas: json([conta('1', '111'), conta('2', '111'), conta('3', '999')]),
  });

  try {
    assert.deepEqual(await listarTodos(), [
      {
        nome: 'Geniéve',
        cpf: '111',
        email: '111@bantads.com.br',
        telefone: '41999990000',
        quantidadeClientes: 2,
        _links: undefined,
      },
    ]);
  } finally {
    ms.restaurar();
  }
});

test('gerente sem nenhuma conta aparece com 0 clientes', async () => {
  const ms = comMicrosservicos({
    gerentes: json([gerente('111', 'Gadamântio')]),
    contas: json([conta('1', '999')]),
  });

  try {
    const [resultado] = await listarTodos();
    assert.equal(resultado.quantidadeClientes, 0);
  } finally {
    ms.restaurar();
  }
});

test('ordena por nome sem deixar os acentuados para o fim', async () => {
  const ms = comMicrosservicos({
    gerentes: json([gerente('1', 'Zuleica'), gerente('2', 'Érica'), gerente('3', 'Godophredo')]),
    contas: json([]),
  });

  try {
    const nomes = (await listarTodos()).map((g) => g.nome);
    assert.deepEqual(nomes, ['Érica', 'Godophredo', 'Zuleica']);
  } finally {
    ms.restaurar();
  }
});

test('não lista gerente inativo', async () => {
  const ms = comMicrosservicos({
    gerentes: json([gerente('1', 'Ativo'), gerente('2', 'Removido', { ativo: false })]),
    contas: json([conta('1', '2')]),
  });

  try {
    const nomes = (await listarTodos()).map((g) => g.nome);
    assert.deepEqual(nomes, ['Ativo']);
  } finally {
    ms.restaurar();
  }
});

test('repassa os _links criados pelo MS Gerente', async () => {
  const _links = { self: { href: 'http://ms-gerente/gerentes/1' } };
  const ms = comMicrosservicos({
    gerentes: json([gerente('1', 'Geniéve', { _links })]),
    contas: json([]),
  });

  try {
    const [resultado] = await listarTodos();
    assert.deepEqual(resultado._links, _links);
  } finally {
    ms.restaurar();
  }
});

test('erro do MS Gerente é propagado', async () => {
  const ms = comMicrosservicos({ gerentes: json({}, 500), contas: json([]) });

  try {
    await assert.rejects(() => listarTodos(), /MS Gerente respondeu 500/);
  } finally {
    ms.restaurar();
  }
});

test('erro do MS Conta é propagado', async () => {
  const ms = comMicrosservicos({ gerentes: json([gerente('1', 'Geniéve')]), contas: json({}, 500) });

  try {
    await assert.rejects(() => listarTodos(), /MS Conta respondeu 500/);
  } finally {
    ms.restaurar();
  }
});
