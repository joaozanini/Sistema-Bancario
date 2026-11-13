const clientesService = require('../services/clientes.service');
const clienteService = require('../services/cliente.service');
const { responderErro } = require('../services/erro.service');

// R11: a lista vai dentro de { clientes }, com os _links da própria listagem
async function listar(req, res) {
  try {
    const clientes = await clientesService.listarTodos(req.query.busca);
    return res.json({ clientes, _links: { self: { href: '/clientes' } } });
  } catch (err) {
    console.error('Falha ao consultar clientes (API Composition):', err.message);
    return responderErro(res, 502, 'Serviço de clientes indisponível.');
  }
}

// gerente consulta qualquer cliente; cliente só o próprio cadastro
async function buscar(req, res) {
  const { cpf } = req.params;
  if (String(req.user.tipo).toUpperCase() !== 'GERENTE' && req.user.cpf !== cpf) {
    return responderErro(res, 403, 'Acesso negado: o cadastro é de outro cliente.');
  }

  try {
    return res.json(await clienteService.buscar(cpf));
  } catch (err) {
    if (err instanceof clienteService.ClienteNaoEncontradoError) {
      return responderErro(res, 404, 'Cliente não encontrado.');
    }
    console.error('Falha ao consultar cliente no MS Cliente:', err.message);
    return responderErro(res, 502, 'Serviço de clientes indisponível.');
  }
}

module.exports = { listar, buscar };
