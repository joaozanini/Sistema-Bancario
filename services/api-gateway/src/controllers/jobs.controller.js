const jobService = require('../services/job.service');

async function buscarOuResponder404(req, res) {
  const job = await jobService.buscar(req.params.id);
  if (!job) {
    res.status(404).json({ message: 'Job não encontrado ou expirado.' });
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
    return res.status(503).json({ message: 'Serviço de jobs indisponível.' });
  }
}

async function resultado(req, res) {
  try {
    const job = await buscarOuResponder404(req, res);
    if (!job) return undefined;

    if (job.resultType !== 'inline') {
      return res.status(409).json({
        message: 'Este job não tem resultado inline. Consulte /jobs/:id/status.',
      });
    }
    return res.json(job.resultado);
  } catch (err) {
    console.error('Falha ao consultar resultado do job:', err.message);
    return res.status(503).json({ message: 'Serviço de jobs indisponível.' });
  }
}

module.exports = { status, resultado };
