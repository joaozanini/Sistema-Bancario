const { randomUUID } = require('node:crypto');
const jobService = require('../services/job.service');
const relatorioService = require('../services/relatorio.service');
const { responderErro } = require('../services/erro.service');
const { responderJobAceito } = require('./jobs.controller');

const DOMINIO = 'relatorios';

// R16: cria o job, responde 202 e compõe o relatório em segundo plano
async function gerar(req, res) {
  const jobId = randomUUID();

  let job;
  try {
    job = await jobService.criar(jobId, DOMINIO);
  } catch (err) {
    console.error('Falha ao criar o job do relatório:', err.message);
    return responderErro(res, 503, 'Serviço de jobs indisponível.');
  }

  responderJobAceito(res, job);
  comporEmSegundoPlano(jobId);
  return undefined;
}

// compõe o relatório e conclui o job ou marca falha
async function comporEmSegundoPlano(jobId) {
  try {
    const linhas = await relatorioService.gerar();
    // resultado inline no formato { clientes }
    await jobService.concluirInline(jobId, { clientes: linhas });
  } catch (err) {
    console.error('Falha ao gerar o relatório de clientes:', err.message);
    try {
      await jobService.falhar(jobId, 'Não foi possível gerar o relatório de clientes.');
    } catch (erroJob) {
      console.error('Falha ao registrar o erro no job:', erroJob.message);
    }
  }
}

module.exports = { gerar, DOMINIO };
