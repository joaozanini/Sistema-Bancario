const { randomUUID } = require('node:crypto');
const rabbit = require('../config/rabbit');
const jobService = require('./job.service');

const FILA_SAGA_CMD = 'saga.cmd';

// formato do enunciado (5.7): 2026-04-30T10:00:00, sem milissegundos e sem fuso,
// em horário local como o Orquestrador (toISOString devolveria UTC com .000Z)
function timestampAgora() {
  const local = new Date(Date.now() - new Date().getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 19);
}

// jobId e sagaId são o mesmo UUID (enunciado 5.8): o Orquestrador recebe o sagaId
// na mensagem e usa ele para atualizar o job no Redis ao terminar a SAGA.
async function iniciar(tipo, dominio, payload) {
  const sagaId = randomUUID();

  // o job vem antes da publicação: se fosse depois, o Orquestrador poderia
  // responder antes do job existir e a atualização dele se perderia
  await jobService.criar(sagaId, dominio);

  await rabbit.publicar(FILA_SAGA_CMD, {
    sagaId,
    tipo,
    timestamp: timestampAgora(),
    payload,
  });

  return sagaId;
}

module.exports = { iniciar, FILA_SAGA_CMD };
