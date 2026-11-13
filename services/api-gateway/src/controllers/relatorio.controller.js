const { randomUUID } = require('node:crypto');
const jobService = require('../services/job.service');
const relatorioService = require('../services/relatorio.service');
const { responderErro } = require('../services/erro.service');
const { responderJobAceito } = require('./jobs.controller');

const DOMINIO = 'relatorios';

// R16 é assíncrono sem ser SAGA: nenhum dado é alterado, só composto. Por isso o
// jobId nasce aqui e não vem do Orquestrador — quem conclui o job é o Gateway.
async function gerar(req, res) {
  const jobId = randomUUID();

  let job;
  try {
    job = await jobService.criar(jobId, DOMINIO);
  } catch (err) {
    console.error('Falha ao criar o job do relatório:', err.message);
    return responderErro(res, 503, 'Serviço de jobs indisponível.');
  }

  // 202 primeiro: a composição percorre todas as contas e não pode prender a
  // resposta. O front acompanha por /jobs/:id/status.
  responderJobAceito(res, job);
  comporEmSegundoPlano(jobId);
  return undefined;
}

// Chamada sem await de propósito (o 202 já foi enviado). Todo erro é tratado aqui
// dentro, então a promise devolvida nunca rejeita.
async function comporEmSegundoPlano(jobId) {
  try {
    const linhas = await relatorioService.gerar();
    // o resultado inline do R16 é um objeto { clientes: [...] }, não a lista solta
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
