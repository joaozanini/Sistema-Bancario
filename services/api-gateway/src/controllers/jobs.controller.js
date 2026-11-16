const jobService = require('../services/job.service');
const { responderErro } = require('../services/erro.service');

// responde 202 com o job e o Location do status
function responderJobAceito(res, job) {
  return res.status(202).location(`/jobs/${job.jobId}/status`).json(job);
}

async function buscarOuResponder404(req, res) {
  const job = await jobService.buscar(req.params.id);
  if (!job) {
    responderErro(res, 404, 'Job não encontrado ou expirado.');
    return null;
  }
  return job;
}

async function status(req, res) {
  try {
    const job = await buscarOuResponder404(req, res);
    if (!job) return undefined;

    // devolve o job sem o resultado
    const { resultado, ...semResultado } = job;
    return res.json(semResultado);
  } catch (err) {
    console.error('Falha ao consultar job:', err.message);
    return responderErro(res, 503, 'Serviço de jobs indisponível.');
  }
}

async function resultado(req, res) {
  try {
    const job = await buscarOuResponder404(req, res);
    if (!job) return undefined;

    // 409 se o job não tem resultado inline
    if (job.resultType !== 'inline') {
      return responderErro(res, 409, 'Este job não tem resultado inline. Consulte /jobs/:id/status.');
    }
    return res.json(job.resultado);
  } catch (err) {
    console.error('Falha ao consultar resultado do job:', err.message);
    return responderErro(res, 503, 'Serviço de jobs indisponível.');
  }
}

module.exports = { status, resultado, responderJobAceito };
