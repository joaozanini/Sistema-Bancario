const { randomUUID } = require('node:crypto');
const jobService = require('../services/job.service');
const relatorioService = require('../services/relatorio.service');

const DOMINIO = 'relatorios';

// R16 é assíncrono sem ser SAGA: nenhum dado é alterado, só composto. Por isso o
// jobId nasce aqui e não vem do Orquestrador — quem conclui o job é o Gateway.
async function gerar(req, res) {
  const jobId = randomUUID();

  try {
    await jobService.criar(jobId, DOMINIO);
  } catch (err) {
    console.error('Falha ao criar o job do relatório:', err.message);
    return res.status(503).json({ message: 'Serviço de jobs indisponível.' });
  }

  // 202 primeiro: a composição percorre todas as contas e não pode prender a
  // resposta. O front acompanha por /jobs/:id/status.
  res.status(202).json({ jobId });
  comporEmSegundoPlano(jobId);
  return undefined;
}

function comporEmSegundoPlano(jobId) {
  relatorioService
    .gerar()
    .then((linhas) => jobService.concluirInline(jobId, linhas))
    .catch(async (err) => {
      console.error('Falha ao gerar o relatório de clientes:', err.message);
      try {
        await jobService.falhar(jobId, 'Não foi possível gerar o relatório de clientes.');
      } catch (erroJob) {
        console.error('Falha ao registrar o erro no job:', erroJob.message);
      }
    });
}

module.exports = { gerar, DOMINIO };
