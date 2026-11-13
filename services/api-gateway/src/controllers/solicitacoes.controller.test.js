const test = require('node:test');
const assert = require('node:assert/strict');

const sagaService = require('../services/saga.service');
const { aprovar, TIPO_APROVAR_CLIENTE, DOMINIO } = require('./solicitacoes.controller');

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

// o que o saga.service devolve ao criar o job: é o corpo do 202
function jobPendente(jobId, dominio) {
  return { jobId, status: 'PENDENTE', resultType: null, dominio, resourceId: null, erro: null };
}

function comSagaDublada(implementacao) {
  const original = sagaService.iniciar;
  sagaService.iniciar = implementacao;
  return () => {
    sagaService.iniciar = original;
  };
}

test('aprovar devolve 202 com o job da saga e o Location do polling', async () => {
  const restaurar = comSagaDublada(async () => jobPendente('job-123', 'clientes'));
  try {
    const res = resposta();
    await aprovar({ params: { cpf: '11122233344' } }, res);

    assert.equal(res.codigo, 202);
    assert.deepEqual(res.corpo, jobPendente('job-123', 'clientes'));
    assert.equal(res.cabecalhos.Location, '/jobs/job-123/status');
  } finally {
    restaurar();
  }
});

test('publica o tipo e o dominio que o orquestrador espera', async () => {
  const recebido = [];
  const restaurar = comSagaDublada(async (tipo, dominio, payload) => {
    recebido.push({ tipo, dominio, payload });
    return jobPendente('job-1', 'clientes');
  });
  try {
    await aprovar({ params: { cpf: '11122233344' } }, resposta());

    // precisa bater com AprovarClienteSaga.TIPO e dominioJob() no ms-orquestrador
    assert.equal(recebido[0].tipo, 'aprovar-cliente');
    assert.equal(recebido[0].dominio, 'clientes');
    assert.deepEqual(recebido[0].payload, { cpf: '11122233344' });
  } finally {
    restaurar();
  }
});

test('as constantes exportadas sao as mesmas usadas na publicacao', () => {
  assert.equal(TIPO_APROVAR_CLIENTE, 'aprovar-cliente');
  assert.equal(DOMINIO, 'clientes');
});

test('falha ao publicar vira 502 e nao 202', async () => {
  const restaurar = comSagaDublada(async () => {
    throw new Error('broker fora do ar');
  });
  try {
    const res = resposta();
    await aprovar({ params: { cpf: '11122233344' } }, res);

    assert.equal(res.codigo, 502);
    assert.equal(res.corpo.erro, 'Bad Gateway');
    assert.ok(res.corpo.mensagem);
  } finally {
    restaurar();
  }
});
