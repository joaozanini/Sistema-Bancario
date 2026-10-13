const { clienteServiceUrl } = require('../config/services');

class ClienteNaoEncontradoError extends Error {}

async function buscar(cpf) {
  const resposta = await fetch(`${clienteServiceUrl}/clientes/${encodeURIComponent(cpf)}`);

  if (resposta.status === 404) {
    throw new ClienteNaoEncontradoError();
  }
  if (!resposta.ok) {
    throw new Error(`MS Cliente respondeu ${resposta.status}`);
  }

  return resposta.json();
}

async function buscarNome(cpf) {
  const { nome } = await buscar(cpf);
  if (!nome) {
    throw new Error(`MS Cliente não retornou o nome do CPF ${cpf}`);
  }
  return nome;
}

module.exports = { buscar, buscarNome, ClienteNaoEncontradoError };
