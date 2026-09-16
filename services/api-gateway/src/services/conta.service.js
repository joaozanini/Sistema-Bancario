const { contaServiceUrl } = require('../config/services');

class ContaNaoEncontradaError extends Error {}

async function buscarCpfTitular(numeroConta) {
  const resposta = await fetch(`${contaServiceUrl}/contas/${encodeURIComponent(numeroConta)}`);

  if (resposta.status === 404) {
    throw new ContaNaoEncontradaError();
  }
  if (!resposta.ok) {
    throw new Error(`MS Conta respondeu ${resposta.status}`);
  }

  const { cpfCliente } = await resposta.json();
  if (!cpfCliente) {
    throw new Error(`MS Conta não retornou cpfCliente da conta ${numeroConta}`);
  }
  return cpfCliente;
}

module.exports = { buscarCpfTitular, ContaNaoEncontradaError };
