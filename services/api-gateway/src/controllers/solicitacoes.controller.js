const sagaService = require('../services/saga.service');

const TIPO_APROVAR_CLIENTE = 'aprovar-cliente';
const DOMINIO = 'clientes';

// R9: aprovar é SAGA, então o Gateway só publica e devolve 202 — quem conclui é o
// Orquestrador, e o front acompanha por /jobs/:id/status.
async function aprovar(req, res) {
  const { cpf } = req.params;

  try {
    const jobId = await sagaService.iniciar(TIPO_APROVAR_CLIENTE, DOMINIO, { cpf });
    return res.status(202).json({ jobId });
  } catch (err) {
    console.error('Falha ao iniciar a SAGA de aprovação:', err.message);
    return res.status(502).json({ message: 'Não foi possível iniciar a aprovação.' });
  }
}

module.exports = { aprovar, TIPO_APROVAR_CLIENTE, DOMINIO };
