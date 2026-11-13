const jobService = require('../services/job.service');
const { responderErro } = require('../services/erro.service');

// Resposta das operações assíncronas (R9, R13, R15, R16): 202 com o job no corpo e,
// no Location, o endereço em que o front acompanha o andamento.
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

    // o payload do resultado sai daqui: quem precisa dele busca em /jobs/:id/result
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

    // pendente, com falha ou concluído com resultType "resource": não há resultado inline
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
