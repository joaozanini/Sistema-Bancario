const redis = require('../config/redis');

const JOB_TTL_SECONDS = 5 * 60;

const chave = (jobId) => `job:${jobId}`;
const expiraEm = (segundos) => ({ expiration: { type: 'EX', value: segundos } });

async function criar(jobId, dominio) {
  const job = {
    jobId,
    status: 'PENDENTE',
    resultType: null,
    dominio,
    resourceId: null,
    erro: null,
  };
  await redis.set(chave(jobId), JSON.stringify(job), expiraEm(JOB_TTL_SECONDS));
  return job;
}

async function buscar(jobId) {
  const bruto = await redis.get(chave(jobId));
  return bruto ? JSON.parse(bruto) : null;
}

module.exports = { criar, buscar, JOB_TTL_SECONDS };
