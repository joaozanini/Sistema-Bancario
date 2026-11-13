const test = require('node:test');
const assert = require('node:assert/strict');

const rabbit = require('../config/rabbit');
const jobService = require('./job.service');
const sagaService = require('./saga.service');


function comDublos({ aoPublicar } = {}) {
  const eventos = [];
  const criarOriginal = jobService.criar;
  const publicarOriginal = rabbit.publicar;

  jobService.criar = async (jobId, dominio) => {
    eventos.push({ tipo: 'job', jobId, dominio });
    return { jobId, status: 'PENDENTE', resultType: null, dominio, resourceId: null, erro: null };
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
    const { jobId: sagaId } = await sagaService.iniciar('cliente.aprovar', 'clientes', { cpf: '12912861012' });
    const { fila, mensagem } = d.eventos.find((e) => e.tipo === 'publicacao');

    assert.equal(fila, 'saga.cmd');
    assert.deepEqual(Object.keys(mensagem).sort(), ['payload', 'sagaId', 'timestamp', 'tipo']);
    assert.equal(mensagem.sagaId, sagaId);
    assert.equal(mensagem.tipo, 'cliente.aprovar');
    assert.deepEqual(mensagem.payload, { cpf: '12912861012' });
    assert.match(mensagem.timestamp, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/, 'sem milissegundos e sem fuso');
    assert.ok(!Number.isNaN(Date.parse(mensagem.timestamp)), 'timestamp deve ser uma data válida');
  } finally {
    d.restaurar();
  }
});

test('timestamp é o horário local, como o do Orquestrador', async () => {
  const d = comDublos();
  const relogio = Date.now;
  // 30/04/2026 10:00:00 no horário local da máquina, qualquer que seja o fuso
  const instante = new Date(2026, 3, 30, 10, 0, 0).getTime();
  Date.now = () => instante;
  try {
    await sagaService.iniciar('cliente.aprovar', 'clientes', {});
    const { mensagem } = d.eventos.find((e) => e.tipo === 'publicacao');
    assert.equal(mensagem.timestamp, '2026-04-30T10:00:00');
  } finally {
    Date.now = relogio;
    d.restaurar();
  }
});

test('jobId e sagaId são o mesmo uuid', async () => {
  const d = comDublos();
  try {
    const devolvido = await sagaService.iniciar('gerente.inserir', 'gerentes', {});
    const job = d.eventos.find((e) => e.tipo === 'job');
    const { mensagem } = d.eventos.find((e) => e.tipo === 'publicacao');

    assert.equal(job.jobId, mensagem.sagaId);
    assert.equal(job.dominio, 'gerentes');
    assert.match(mensagem.sagaId, /^[0-9a-f-]{36}$/);
    // quem chama recebe o job PENDENTE, que vira o corpo do 202
    assert.equal(devolvido.jobId, mensagem.sagaId);
    assert.equal(devolvido.status, 'PENDENTE');
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
