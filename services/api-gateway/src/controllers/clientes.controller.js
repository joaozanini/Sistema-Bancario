const clientesService = require('../services/clientes.service');

async function listar(req, res) {
  try {
    return res.json(await clientesService.listarTodos());
  } catch (err) {
    console.error('Falha ao consultar clientes (API Composition):', err.message);
    return res.status(502).json({ message: 'Serviço de clientes indisponível.' });
  }
}

module.exports = { listar };
