const { clienteServiceUrl } = require('../config/services');

class ClienteNaoEncontradoError extends Error {}

async function buscarNome(cpf) {
  const resposta = await fetch(`${clienteServiceUrl}/clientes/${encodeURIComponent(cpf)}`);

  if (resposta.status === 404) {
    throw new ClienteNaoEncontradoError();
  }
  if (!resposta.ok) {
    throw new Error(`MS Cliente respondeu ${resposta.status}`);
  }

  const { nome } = await resposta.json();
  if (!nome) {
    throw new Error(`MS Cliente não retornou o nome do CPF ${cpf}`);
  }
  return nome;
}

module.exports = { buscarNome, ClienteNaoEncontradoError };
