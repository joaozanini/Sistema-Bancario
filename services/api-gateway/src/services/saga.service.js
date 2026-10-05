const { randomUUID } = require('node:crypto');
const rabbit = require('../config/rabbit');
const jobService = require('./job.service');

const FILA_SAGA_CMD = 'saga.cmd';

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
    timestamp: new Date().toISOString(),
    payload,
  });

  return sagaId;
}

module.exports = { iniciar, FILA_SAGA_CMD };
