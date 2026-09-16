const contaService = require('../services/conta.service');

// R6: a conta destino tem de existir — o Gateway resolve o CPF do titular antes de rotear ao MS Conta
async function enriquecerCpfDestino(req, res, next) {
  const { contaDestino } = req.body || {};
  if (!contaDestino) {
    return res.status(400).json({ message: 'Conta destino é obrigatória.' });
  }

  try {
    req.body.cpfDestino = await contaService.buscarCpfTitular(contaDestino);
  } catch (err) {
    if (err instanceof contaService.ContaNaoEncontradaError) {
      return res.status(404).json({ message: 'Conta destino não encontrada.' });
    }
    console.error('Falha ao consultar a conta destino:', err.message);
    return res.status(502).json({ message: 'Serviço de contas indisponível.' });
  }

  return next();
}

module.exports = { enriquecerCpfDestino };
