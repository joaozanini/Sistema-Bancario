const contaService = require('../services/conta.service');
const clienteService = require('../services/cliente.service');

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

// R6: o MS Conta grava os nomes no evento mas não os conhece — quem resolve é o
// Gateway. Sem isso o extrato (R7) fica sem o nome da contraparte.
async function enriquecerNomes(req, res, next) {
  const cpfOrigem = req.user.cpf;
  const { cpfDestino } = req.body;

  try {
    const [nomeOrigem, nomeDestino] = await Promise.all([
      clienteService.buscarNome(cpfOrigem),
      clienteService.buscarNome(cpfDestino),
    ]);

    req.body.cpfOrigem = cpfOrigem;
    req.body.nomeOrigem = nomeOrigem;
    req.body.nomeDestino = nomeDestino;
  } catch (err) {
    if (err instanceof clienteService.ClienteNaoEncontradoError) {
      return res.status(404).json({ message: 'Cliente de origem ou destino não encontrado.' });
    }
    console.error('Falha ao consultar nomes no MS Cliente:', err.message);
    return res.status(502).json({ message: 'Serviço de clientes indisponível.' });
  }

  return next();
}

module.exports = { enriquecerCpfDestino, enriquecerNomes };
