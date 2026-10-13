const test = require('node:test');
const assert = require('node:assert/strict');

const sagaService = require('../services/saga.service');
const redis = require('../config/redis');
const { aprovar, TIPO_APROVAR_CLIENTE, DOMINIO } = require('./solicitacoes.controller');

test.after(() => redis.destroy());

function resposta() {
  return {
    codigo: null,
    corpo: null,
    status(c) {
      this.codigo = c;
      return this;
    },
    json(c) {
      this.corpo = c;
      return this;
    },
  };
}

function comSagaDublada(implementacao) {
  const original = sagaService.iniciar;
  sagaService.iniciar = implementacao;
  return () => {
    sagaService.iniciar = original;
  };
}

test('aprovar devolve 202 com o jobId da saga', async () => {
  const restaurar = comSagaDublada(async () => 'job-123');
  try {
    const res = resposta();
    await aprovar({ params: { cpf: '11122233344' } }, res);

    assert.equal(res.codigo, 202);
    assert.deepEqual(res.corpo, { jobId: 'job-123' });
  } finally {
    restaurar();
  }
});

test('publica o tipo e o dominio que o orquestrador espera', async () => {
  const recebido = [];
  const restaurar = comSagaDublada(async (tipo, dominio, payload) => {
    recebido.push({ tipo, dominio, payload });
    return 'job-1';
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
    assert.ok(res.corpo.message);
  } finally {
    restaurar();
  }
});
