const test = require('node:test');
const assert = require('node:assert/strict');

const jobService = require('../services/job.service');
const { status, resultado, responderJobAceito } = require('./jobs.controller');

function resposta() {
  return {
    codigo: null,
    corpo: null,
    cabecalhos: {},
    status(c) {
      this.codigo = c;
      return this;
    },
    location(url) {
      this.cabecalhos.Location = url;
      return this;
    },
    json(c) {
      this.corpo = c;
      return this;
    },
  };
}

function comJob(job) {
  const original = jobService.buscar;
  jobService.buscar = async () => job;
  return () => {
    jobService.buscar = original;
  };
}

const PENDENTE = { jobId: 'job-1', status: 'PENDENTE', resultType: null, dominio: 'clientes', resourceId: null, erro: null };
const INLINE = {
  jobId: 'job-2',
  status: 'CONCLUIDO',
  resultType: 'inline',
  dominio: 'relatorios',
  resourceId: null,
  erro: null,
  resultado: { clientes: [{ cpf: '12912861012', nome: 'Catharyna' }] },
};

test('202 leva o job no corpo e o endereco de polling no Location', () => {
  const res = resposta();
  responderJobAceito(res, PENDENTE);

  assert.equal(res.codigo, 202);
  assert.deepEqual(res.corpo, PENDENTE);
  assert.equal(res.cabecalhos.Location, '/jobs/job-1/status');
});

test('status devolve o job sem o resultado inline', async () => {
  const restaurar = comJob(INLINE);
  try {
    const res = resposta();
    await status({ params: { id: 'job-2' } }, res);

    assert.equal(res.corpo.status, 'CONCLUIDO');
    assert.equal(res.corpo.resultType, 'inline');
    assert.equal('resultado' in res.corpo, false);
  } finally {
    restaurar();
  }
});

test('job inexistente ou expirado responde 404 no formato do contrato', async () => {
  const restaurar = comJob(null);
  try {
    const res = resposta();
    await status({ params: { id: 'nao-existe' } }, res);

    assert.equal(res.codigo, 404);
    assert.deepEqual(res.corpo, { status: 404, erro: 'Not Found', mensagem: 'Job não encontrado ou expirado.' });
  } finally {
    restaurar();
  }
});

test('result devolve o resultado inline do job concluido', async () => {
  const restaurar = comJob(INLINE);
  try {
    const res = resposta();
    await resultado({ params: { id: 'job-2' } }, res);

    assert.equal(res.codigo, null);
    assert.deepEqual(res.corpo, INLINE.resultado);
  } finally {
    restaurar();
  }
});

test('result de job pendente (ou sem resultado inline) responde 409', async () => {
  const restaurar = comJob(PENDENTE);
  try {
    const res = resposta();
    await resultado({ params: { id: 'job-1' } }, res);

    assert.equal(res.codigo, 409);
    assert.equal(res.corpo.erro, 'Conflict');
  } finally {
    restaurar();
  }
});
