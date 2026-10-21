const clientesService = require('../services/clientes.service');
const clienteService = require('../services/cliente.service');

async function listar(req, res) {
  try {
    return res.json(await clientesService.listarTodos());
  } catch (err) {
    console.error('Falha ao consultar clientes (API Composition):', err.message);
    return res.status(502).json({ message: 'Serviço de clientes indisponível.' });
  }
}

// gerente consulta qualquer cliente; cliente só o próprio cadastro
async function buscar(req, res) {
  const { cpf } = req.params;
  if (String(req.user.tipo).toUpperCase() !== 'GERENTE' && req.user.cpf !== cpf) {
    return res.status(403).json({ message: 'Acesso negado.' });
  }

  try {
    return res.json(await clienteService.buscar(cpf));
  } catch (err) {
    if (err instanceof clienteService.ClienteNaoEncontradoError) {
      return res.status(404).json({ message: 'Cliente não encontrado.' });
    }
    console.error('Falha ao consultar cliente no MS Cliente:', err.message);
    return res.status(502).json({ message: 'Serviço de clientes indisponível.' });
  }
}

module.exports = { listar, buscar };
