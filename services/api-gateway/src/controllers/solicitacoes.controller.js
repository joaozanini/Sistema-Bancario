const sagaService = require('../services/saga.service');
const clienteService = require('../services/cliente.service');
const { responderErro } = require('../services/erro.service');
const { responderJobAceito } = require('./jobs.controller');

const TIPO_APROVAR_CLIENTE = 'aprovar-cliente';
const DOMINIO = 'clientes';

// R9: aprovar é SAGA, então o Gateway só publica e devolve 202 — quem conclui é o
// Orquestrador, e o front acompanha por /jobs/:id/status. O estado da solicitação
// não é conferido aqui: inexistente ou já processada termina como job FALHA.
async function aprovar(req, res) {
  const { cpf } = req.params;

  try {
    const job = await sagaService.iniciar(TIPO_APROVAR_CLIENTE, DOMINIO, { cpf });
    // R9 cria o cliente: o que estiver em cache para esse CPF fica obsoleto
    await clienteService.invalidar(cpf);
    return responderJobAceito(res, job);
  } catch (err) {
    console.error('Falha ao iniciar a SAGA de aprovação:', err.message);
    return responderErro(res, 502, 'Não foi possível iniciar a aprovação.');
  }
}

module.exports = { aprovar, TIPO_APROVAR_CLIENTE, DOMINIO };
