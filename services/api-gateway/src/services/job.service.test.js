const test = require('node:test');
const assert = require('node:assert/strict');

const redis = require('../config/redis');
const jobService = require('./job.service');

const JOB_ID = 'f1d2e3c4-0000-4000-8000-000000000001';
const TTL = { expiration: { type: 'EX', value: 300 } };

function comRedisDublado({ guardado } = {}) {
  const originais = { get: redis.get, set: redis.set };
  const gravacoes = [];
  let atual = guardado;

  redis.get = async () => (atual === undefined ? null : atual);
  redis.set = async (k, v, opts) => {
    gravacoes.push({ k, job: JSON.parse(v), opts });
    atual = v;
  };

  return {
    gravacoes,
    ultima: () => gravacoes[gravacoes.length - 1],
    restaurar() {
      Object.assign(redis, originais);
    },
  };
}

const jobPendente = JSON.stringify({
  jobId: JOB_ID,
  status: 'PENDENTE',
  resultType: null,
  dominio: 'relatorios',
  resourceId: null,
  erro: null,
});

test('criar grava o formato PENDENTE do enunciado com TTL de 5 min', async () => {
  const d = comRedisDublado();
  try {
    await jobService.criar(JOB_ID, 'relatorios');

    assert.equal(d.ultima().k, `job:${JOB_ID}`);
    assert.deepEqual(d.ultima().opts, TTL);
    assert.deepEqual(d.ultima().job, {
      jobId: JOB_ID,
      status: 'PENDENTE',
      resultType: null,
      dominio: 'relatorios',
      resourceId: null,
      erro: null,
    });
  } finally {
    d.restaurar();
  }
});

test('concluirInline marca CONCLUIDO/inline e guarda o resultado', async () => {
  const d = comRedisDublado({ guardado: jobPendente });
  try {
    const job = await jobService.concluirInline(JOB_ID, [{ nome: 'Catharyna' }]);

    assert.equal(job.status, 'CONCLUIDO');
    assert.equal(job.resultType, 'inline');
    assert.deepEqual(job.resultado, [{ nome: 'Catharyna' }]);
    assert.equal(job.resourceId, null, 'inline não tem resourceId');
    assert.equal(job.erro, null);
    assert.equal(job.dominio, 'relatorios', 'o domínio do job não pode ser perdido na atualização');
    assert.deepEqual(d.ultima().opts, TTL, 'o TTL volta aos 5 min para o front buscar o resultado');
  } finally {
    d.restaurar();
  }
});

test('falhar marca FALHA com o erro e sem resultType', async () => {
  const d = comRedisDublado({ guardado: jobPendente });
  try {
    const job = await jobService.falhar(JOB_ID, 'MS Conta respondeu 500');

    assert.equal(job.status, 'FALHA');
    assert.equal(job.resultType, null, 'FALHA não tem resultType no formato da 5.8');
    assert.equal(job.erro, 'MS Conta respondeu 500');
    assert.equal(job.resultado, undefined, 'falha não pode carregar resultado');
  } finally {
    d.restaurar();
  }
});

test('atualizar job expirado devolve null e nao recria a chave', async () => {
  const d = comRedisDublado();
  try {
    assert.equal(await jobService.concluirInline(JOB_ID, []), null);
    assert.equal(d.gravacoes.length, 0, 'job fora do TTL não pode ser ressuscitado');
  } finally {
    d.restaurar();
  }
});
