const { clienteServiceUrl } = require('../config/services');
const cache = require('./cache.service');

class ClienteNaoEncontradoError extends Error {}

async function buscarNaOrigem(cpf) {
  const resposta = await fetch(`${clienteServiceUrl}/clientes/${encodeURIComponent(cpf)}`);

  if (resposta.status === 404) {
    throw new ClienteNaoEncontradoError();
  }
  if (!resposta.ok) {
    throw new Error(`MS Cliente respondeu ${resposta.status}`);
  }

  return resposta.json();
}

async function buscar(cpf) {
  return cache.lerOuBuscar(cache.DOMINIOS.CLIENTE, cpf, () => buscarNaOrigem(cpf));
}

async function buscarNome(cpf) {
  const { nome } = await buscar(cpf);
  if (!nome) {
    throw new Error(`MS Cliente não retornou o nome do CPF ${cpf}`);
  }
  return nome;
}

function invalidar(cpf) {
  return cache.invalidar(cache.DOMINIOS.CLIENTE, cpf);
}

module.exports = { buscar, buscarNome, invalidar, ClienteNaoEncontradoError };
