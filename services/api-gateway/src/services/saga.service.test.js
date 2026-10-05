const test = require('node:test');
const assert = require('node:assert/strict');

const rabbit = require('../config/rabbit');
const redis = require('../config/redis');
const jobService = require('./job.service');
const sagaService = require('./saga.service');

// job.service puxa o cliente Redis, que fica em loop de reconexão e segura o
// processo. Sem fechar aqui, o arquivo de teste nunca termina.
test.after(() => redis.destroy());

function comDublos({ aoPublicar } = {}) {
  const eventos = [];
  const criarOriginal = jobService.criar;
  const publicarOriginal = rabbit.publicar;

  jobService.criar = async (jobId, dominio) => {
    eventos.push({ tipo: 'job', jobId, dominio });
  };
  rabbit.publicar = async (fila, mensagem) => {
    eventos.push({ tipo: 'publicacao', fila, mensagem });
    if (aoPublicar) aoPublicar();
  };

  return {
    eventos,
    restaurar() {
      jobService.criar = criarOriginal;
      rabbit.publicar = publicarOriginal;
    },
  };
}

test('publica em saga.cmd no formato do enunciado', async () => {
  const d = comDublos();
  try {
    const sagaId = await sagaService.iniciar('cliente.aprovar', 'clientes', { cpf: '12912861012' });
    const { fila, mensagem } = d.eventos.find((e) => e.tipo === 'publicacao');

    assert.equal(fila, 'saga.cmd');
    assert.deepEqual(Object.keys(mensagem).sort(), ['payload', 'sagaId', 'timestamp', 'tipo']);
    assert.equal(mensagem.sagaId, sagaId);
    assert.equal(mensagem.tipo, 'cliente.aprovar');
    assert.deepEqual(mensagem.payload, { cpf: '12912861012' });
    assert.ok(!Number.isNaN(Date.parse(mensagem.timestamp)), 'timestamp deve ser ISO válido');
  } finally {
    d.restaurar();
  }
});

test('jobId e sagaId são o mesmo uuid', async () => {
  const d = comDublos();
  try {
    const sagaId = await sagaService.iniciar('gerente.inserir', 'gerentes', {});
    const job = d.eventos.find((e) => e.tipo === 'job');

    assert.equal(job.jobId, sagaId);
    assert.equal(job.dominio, 'gerentes');
    assert.match(sagaId, /^[0-9a-f-]{36}$/);
  } finally {
    d.restaurar();
  }
});

test('cria o job antes de publicar', async () => {
  const d = comDublos();
  try {
    await sagaService.iniciar('gerente.remover', 'gerentes', {});
    assert.deepEqual(d.eventos.map((e) => e.tipo), ['job', 'publicacao']);
  } finally {
    d.restaurar();
  }
});

test('falha ao publicar propaga, para o chamador nao responder 202', async () => {
  const d = comDublos({
    aoPublicar() {
      throw new Error('broker fora do ar');
    },
  });
  try {
    await assert.rejects(() => sagaService.iniciar('cliente.aprovar', 'clientes', {}), /broker fora do ar/);
  } finally {
    d.restaurar();
  }
});
