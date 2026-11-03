const test = require('node:test');
const assert = require('node:assert/strict');

const redis = require('../config/redis');
const cache = require('./cache.service');

const CPF = '12912861012';
const CHAVE = `cache:cliente:${CPF}`;

function comRedisDublado({ get, set, del } = {}) {
  const originais = { get: redis.get, set: redis.set, del: redis.del };
  const chamadas = [];

  redis.get = async (k) => {
    chamadas.push({ op: 'get', k });
    return get ? get(k) : null;
  };
  redis.set = async (k, v, opts) => {
    chamadas.push({ op: 'set', k, v, opts });
    if (set) set(k, v, opts);
  };
  redis.del = async (k) => {
    chamadas.push({ op: 'del', k });
    if (del) del(k);
  };

  return {
    chamadas,
    restaurar() {
      Object.assign(redis, originais);
    },
  };
}

test('miss vai na origem e grava no cache com TTL de 5 min', async () => {
  const d = comRedisDublado();
  let foiNaOrigem = 0;

  try {
    const dados = await cache.lerOuBuscar('cliente', CPF, async () => {
      foiNaOrigem += 1;
      return { cpf: CPF, nome: 'Catharyna' };
    });

    assert.equal(foiNaOrigem, 1);
    assert.deepEqual(dados, { cpf: CPF, nome: 'Catharyna' });

    const gravou = d.chamadas.find((c) => c.op === 'set');
    assert.equal(gravou.k, CHAVE);
    assert.deepEqual(gravou.opts, { expiration: { type: 'EX', value: 300 } });
  } finally {
    d.restaurar();
  }
});

test('hit nao toca na origem', async () => {
  const d = comRedisDublado({ get: () => JSON.stringify({ cpf: CPF, nome: 'Do cache' }) });
  let foiNaOrigem = 0;

  try {
    const dados = await cache.lerOuBuscar('cliente', CPF, async () => {
      foiNaOrigem += 1;
      return { nome: 'Da origem' };
    });

    assert.equal(foiNaOrigem, 0, 'nao pode chamar a origem quando ha cache');
    assert.equal(dados.nome, 'Do cache');
    assert.equal(d.chamadas.filter((c) => c.op === 'set').length, 0);
  } finally {
    d.restaurar();
  }
});

test('invalidar apaga exatamente a chave do enunciado', async () => {
  const d = comRedisDublado();
  try {
    await cache.invalidar('gerente', '98574307084');
    assert.deepEqual(
      d.chamadas.filter((c) => c.op === 'del').map((c) => c.k),
      ['cache:gerente:98574307084'],
    );
  } finally {
    d.restaurar();
  }
});

test('redis fora do ar na leitura degrada para a origem', async () => {
  const d = comRedisDublado({
    get() {
      throw new Error('conexao recusada');
    },
  });

  try {
    const dados = await cache.lerOuBuscar('cliente', CPF, async () => ({ nome: 'Da origem' }));
    assert.equal(dados.nome, 'Da origem', 'cache fora do ar nao pode derrubar a requisicao');
  } finally {
    d.restaurar();
  }
});

test('redis fora do ar na escrita ainda devolve o dado', async () => {
  const d = comRedisDublado({
    set() {
      throw new Error('conexao recusada');
    },
  });

  try {
    const dados = await cache.lerOuBuscar('cliente', CPF, async () => ({ nome: 'Da origem' }));
    assert.equal(dados.nome, 'Da origem');
  } finally {
    d.restaurar();
  }
});

test('os dominios sao so cliente e gerente', () => {
  // dado do MS Conta (saldo, extrato) nao pode ser cacheado: e consistencia eventual
  assert.deepEqual(Object.values(cache.DOMINIOS).sort(), ['cliente', 'gerente']);
  assert.equal(cache.CACHE_TTL_SECONDS, 300);
});
