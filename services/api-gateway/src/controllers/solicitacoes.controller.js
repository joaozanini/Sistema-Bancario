const sagaService = require('../services/saga.service');
const clienteService = require('../services/cliente.service');
const { responderErro } = require('../services/erro.service');
const { responderJobAceito } = require('./jobs.controller');

const TIPO_APROVAR_CLIENTE = 'aprovar-cliente';
const DOMINIO = 'clientes';

// R9: inicia a SAGA de aprovação e responde 202
async function aprovar(req, res) {
  const { cpf } = req.params;

  try {
    const job = await sagaService.iniciar(TIPO_APROVAR_CLIENTE, DOMINIO, { cpf });
    // invalida o cache do cliente
    await clienteService.invalidar(cpf);
    return responderJobAceito(res, job);
  } catch (err) {
    console.error('Falha ao iniciar a SAGA de aprovação:', err.message);
    return responderErro(res, 502, 'Não foi possível iniciar a aprovação.');
  }
}

module.exports = { aprovar, TIPO_APROVAR_CLIENTE, DOMINIO };
