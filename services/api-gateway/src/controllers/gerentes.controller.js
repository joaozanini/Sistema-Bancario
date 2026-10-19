const sagaService = require('../services/saga.service');
const gerenteService = require('../services/gerente.service');

const TIPO_REMOVER_GERENTE = 'remover-gerente';
const DOMINIO = 'gerentes';

// R15: o Gateway recusa a auto-remocao ANTES de publicar, senao a SAGA chegaria
// a inativar o gerente e so depois seria desfeita por compensacao.
async function remover(req, res) {
  const { cpf } = req.params;

  if (req.user.cpf === cpf) {
    return res.status(403).json({ message: 'Um gerente não pode remover a si mesmo.' });
  }

  try {
    const jobId = await sagaService.iniciar(TIPO_REMOVER_GERENTE, DOMINIO, { cpf });
    // R15 inativa o gerente: o cadastro em cache deixa de valer
    await gerenteService.invalidar(cpf);
    return res.status(202).json({ jobId });
  } catch (err) {
    console.error('Falha ao iniciar a SAGA de remoção:', err.message);
    return res.status(502).json({ message: 'Não foi possível iniciar a remoção.' });
  }
}

module.exports = { remover, TIPO_REMOVER_GERENTE, DOMINIO };
