const sagaService = require('../services/saga.service');
const clienteService = require('../services/cliente.service');

const TIPO_APROVAR_CLIENTE = 'aprovar-cliente';
const DOMINIO = 'clientes';

// R9: aprovar é SAGA, então o Gateway só publica e devolve 202 — quem conclui é o
// Orquestrador, e o front acompanha por /jobs/:id/status.
async function aprovar(req, res) {
  const { cpf } = req.params;

  try {
    const jobId = await sagaService.iniciar(TIPO_APROVAR_CLIENTE, DOMINIO, { cpf });
    // R9 cria o cliente: o que estiver em cache para esse CPF fica obsoleto
    await clienteService.invalidar(cpf);
    return res.status(202).json({ jobId });
  } catch (err) {
    console.error('Falha ao iniciar a SAGA de aprovação:', err.message);
    return res.status(502).json({ message: 'Não foi possível iniciar a aprovação.' });
  }
}

module.exports = { aprovar, TIPO_APROVAR_CLIENTE, DOMINIO };
