const { gerenteServiceUrl } = require('../config/services');
const cache = require('./cache.service');

class GerenteNaoEncontradoError extends Error {}

async function buscarNaOrigem(cpf) {
  const resposta = await fetch(`${gerenteServiceUrl}/gerentes/${encodeURIComponent(cpf)}`);

  if (resposta.status === 404) {
    throw new GerenteNaoEncontradoError();
  }
  if (!resposta.ok) {
    throw new Error(`MS Gerente respondeu ${resposta.status}`);
  }

  return resposta.json();
}

async function buscar(cpf) {
  return cache.lerOuBuscar(cache.DOMINIOS.GERENTE, cpf, () => buscarNaOrigem(cpf));
}

function invalidar(cpf) {
  return cache.invalidar(cache.DOMINIOS.GERENTE, cpf);
}

module.exports = { buscar, invalidar, GerenteNaoEncontradoError };
