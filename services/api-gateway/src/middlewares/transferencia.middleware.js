const contaService = require('../services/conta.service');
const clienteService = require('../services/cliente.service');
const { responderErro } = require('../services/erro.service');

// R6: confere se a conta de origem é do usuário logado
async function validarContaOrigem(req, res, next) {
  try {
    const cpfTitular = await contaService.buscarCpfTitular(req.params.numero);
    if (cpfTitular !== req.user.cpf) {
      return responderErro(res, 403, 'Você só pode transferir a partir da sua própria conta.');
    }
  } catch (err) {
    if (err instanceof contaService.ContaNaoEncontradaError) {
      return responderErro(res, 404, 'Conta de origem não encontrada.');
    }
    console.error('Falha ao consultar a conta de origem:', err.message);
    return responderErro(res, 502, 'Serviço de contas indisponível.');
  }

  return next();
}

// R6: adiciona ao corpo o CPF do titular da conta destino
async function enriquecerCpfDestino(req, res, next) {
  const { contaDestino } = req.body || {};
  if (!contaDestino) {
    return responderErro(res, 400, 'Conta destino é obrigatória.');
  }

  try {
    req.body.cpfDestino = await contaService.buscarCpfTitular(contaDestino);
  } catch (err) {
    if (err instanceof contaService.ContaNaoEncontradaError) {
      return responderErro(res, 422, 'Conta destino não encontrada.');
    }
    console.error('Falha ao consultar a conta destino:', err.message);
    return responderErro(res, 502, 'Serviço de contas indisponível.');
  }

  return next();
}

// R6: adiciona ao corpo o CPF e os nomes de origem e destino
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
      return responderErro(res, 422, 'Titular da conta de origem ou de destino não encontrado.');
    }
    console.error('Falha ao consultar nomes no MS Cliente:', err.message);
    return responderErro(res, 502, 'Serviço de clientes indisponível.');
  }

  return next();
}

module.exports = { validarContaOrigem, enriquecerCpfDestino, enriquecerNomes };
