const test = require('node:test');
const assert = require('node:assert/strict');

const redis = require('../config/redis');
const clientesService = require('../services/clientes.service');
const contaService = require('../services/conta.service');
const gerenteService = require('../services/gerente.service');
const { reboot } = require('./reboot.controller');

function comDublos({ resetDe = () => ({ status: 204 }), aoLimpar, aoContar } = {}) {
  const originais = {
    fetch: global.fetch,
    keys: redis.keys,
    del: redis.del,
    listarClientes: clientesService.listarTodos,
    listarGerentes: gerenteService.listarAtivos,
    listarContas: contaService.listar,
  };
  const chamadas = { reset: [], keys: [], del: [] };

  global.fetch = async (url, opcoes) => {
    chamadas.reset.push({ url: String(url), metodo: opcoes?.method });
    const r = resetDe(String(url));
    if (r instanceof Error) throw r;
    return { status: r.status, ok: r.status >= 200 && r.status < 300 };
  };
  redis.keys = async (padrao) => {
    chamadas.keys.push(padrao);
    if (aoLimpar) aoLimpar();
    return [`${padrao.replace('*', '')}abc`];
  };
  redis.del = async (chaves) => {
    chamadas.del.push(chaves);
  };
  clientesService.listarTodos = async () => {
    if (aoContar) aoContar();
    return [1, 2, 3, 4, 5];
  };
  gerenteService.listarAtivos = async () => [1, 2, 3, 4];
  contaService.listar = async () => [1, 2, 3, 4, 5];

  return {
    chamadas,
    restaurar() {
      global.fetch = originais.fetch;
      redis.keys = originais.keys;
      redis.del = originais.del;
      clientesService.listarTodos = originais.listarClientes;
      gerenteService.listarAtivos = originais.listarGerentes;
      contaService.listar = originais.listarContas;
    },
  };
}

function respostaFalsa() {
  return {
    codigo: 200,
    corpo: null,
    status(c) {
      this.codigo = c;
      return this;
    },
    json(b) {
      this.corpo = b;
      return this;
    },
  };
}

test('reseta os quatro servicos e devolve o resumo do enunciado', async () => {
  const d = comDublos();
  try {
    const res = respostaFalsa();
    await reboot({}, res);

    assert.equal(res.codigo, 200);
    assert.equal(res.corpo.status, 'ok');
    assert.deepEqual(res.corpo, { status: 'ok', clientes: 5, gerentes: 4, contas: 5 },
      'o resumo é exatamente o formato da seção 5.11');

    assert.equal(d.chamadas.reset.length, 4, 'cliente, conta, gerente e auth');
    assert.ok(d.chamadas.reset.every((c) => c.metodo === 'POST'));
    assert.ok(d.chamadas.reset.every((c) => c.url.endsWith('/reset')));
  } finally {
    d.restaurar();
  }
});

test('limpa sessoes, jobs, cache e estado das SAGAs do Redis', async () => {
  const d = comDublos();
  try {
    await reboot({}, respostaFalsa());

    // saga:* cobre saga:<id>, saga:<id>:etapa:<n> e o sorted set saga:timeouts;
    // deixar o último faz o agendador do Orquestrador disparar timeout de uma
    // SAGA que o reboot já apagou
    assert.deepEqual(d.chamadas.keys, ['sessao:*', 'job:*', 'cache:*', 'saga:*']);
    assert.equal(d.chamadas.del.length, 4, 'cada padrão com chave encontrada é apagado');
  } finally {
    d.restaurar();
  }
});

test('404 em qualquer /reset e falha', async () => {
  // todos os quatro MSs expõem /reset: um 404 agora é regressão, não tolerância
  const d = comDublos({ resetDe: (url) => (url.includes('8080') ? { status: 404 } : { status: 204 }) });

  try {
    const res = respostaFalsa();
    await reboot({}, res);

    assert.equal(res.codigo, 502);
    assert.equal(res.corpo.servicos.conta, 'respondeu 404');
    assert.equal(d.chamadas.keys.length, 0);
  } finally {
    d.restaurar();
  }
});

test('falha de reset responde 502 e nao limpa o Redis', async () => {
  const d = comDublos({ resetDe: (url) => (url.includes('8080') ? { status: 500 } : { status: 204 }) });
  try {
    const res = respostaFalsa();
    await reboot({}, res);

    assert.equal(res.codigo, 502);
    assert.equal(res.corpo.status, 502);
    assert.equal(res.corpo.erro, 'Bad Gateway');
    assert.ok(res.corpo.mensagem);
    assert.equal(res.corpo.servicos.conta, 'respondeu 500');
    assert.equal(d.chamadas.keys.length, 0, 'sem seed recriado, apagar sessão só piora o estado');
  } finally {
    d.restaurar();
  }
});

test('servico fora do ar responde 502', async () => {
  const d = comDublos({ resetDe: () => new Error('conexao recusada') });
  try {
    const res = respostaFalsa();
    await reboot({}, res);

    assert.equal(res.codigo, 502);
  } finally {
    d.restaurar();
  }
});

test('falha na contagem nao responde ok', async () => {
  const d = comDublos({
    aoContar() {
      throw new Error('MS Conta respondeu 404');
    },
  });

  try {
    const res = respostaFalsa();
    await reboot({}, res);

    assert.equal(res.codigo, 502);
    assert.notEqual(res.corpo.status, 'ok');
  } finally {
    d.restaurar();
  }
});
