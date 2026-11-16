const { randomUUID } = require('node:crypto');
const rabbit = require('../config/rabbit');
const jobService = require('./job.service');

const FILA_SAGA_CMD = 'saga.cmd';

// horário local no formato 2026-04-30T10:00:00
function timestampAgora() {
  const local = new Date(Date.now() - new Date().getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 19);
}

// cria o job com o sagaId, publica o comando em saga.cmd e devolve o job
async function iniciar(tipo, dominio, payload) {
  const sagaId = randomUUID();

  const job = await jobService.criar(sagaId, dominio);

  await rabbit.publicar(FILA_SAGA_CMD, {
    sagaId,
    tipo,
    timestamp: timestampAgora(),
    payload,
  });

  return job;
}

module.exports = { iniciar, FILA_SAGA_CMD };
