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

// atualiza o job e renova o TTL
async function atualizar(jobId, mudancas) {
  const job = await buscar(jobId);
  if (!job) {
    return null;
  }

  const atualizado = { ...job, ...mudancas };
  await redis.set(chave(jobId), JSON.stringify(atualizado), expiraEm(JOB_TTL_SECONDS));
  return atualizado;
}

// conclui o job com resultado inline
function concluirInline(jobId, resultado) {
  return atualizar(jobId, { status: 'CONCLUIDO', resultType: 'inline', resultado });
}

function falhar(jobId, erro) {
  return atualizar(jobId, { status: 'FALHA', resultType: null, erro });
}

module.exports = { criar, buscar, concluirInline, falhar, JOB_TTL_SECONDS };
