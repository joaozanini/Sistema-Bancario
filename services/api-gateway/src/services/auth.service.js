const { authServiceUrl, clienteServiceUrl, gerenteServiceUrl } = require('../config/services');
const tokenService = require('./token.service');
const sessionService = require('./session.service');

class CredenciaisInvalidasError extends Error {}

async function autenticar(email, senha) {
  const respostaAuth = await fetch(`${authServiceUrl}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ login: email, senha }),
  });

  if (respostaAuth.status === 401) {
    throw new CredenciaisInvalidasError();
  }
  if (!respostaAuth.ok) {
    throw new Error(`MS Auth respondeu ${respostaAuth.status}`);
  }

  const { cpf, tipo } = await respostaAuth.json();
  if (!cpf || !tipo) {
    throw new Error('MS Auth não retornou cpf/tipo — sem identidade não há token válido a emitir');
  }

  const usuario = await comporUsuario(cpf, tipo);

  const { token, jti } = tokenService.signToken({ cpf, tipo });
  await sessionService.createSession(jti, cpf, tipo);

  return { token, tipo, usuario };
}

async function comporUsuario(cpf, tipo) {
  const isGerente = String(tipo).toUpperCase() === 'GERENTE';
  const baseUrl = isGerente ? gerenteServiceUrl : clienteServiceUrl;
  const dominio = isGerente ? 'gerentes' : 'clientes';

  const resposta = await fetch(`${baseUrl}/${dominio}/${cpf}`);
  if (!resposta.ok) {
    throw new Error(`Falha ao compor usuario (${dominio}/${cpf}): ${resposta.status}`);
  }
  const dados = await resposta.json();
  return { cpf, nome: dados.nome, email: dados.email };
}

async function encerrarSessao(jti, cpf, expEmSegundos) {
  const restante = expEmSegundos - Math.floor(Date.now() / 1000);
  await sessionService.revokeSession(jti, cpf, restante);
}

module.exports = { autenticar, encerrarSessao, CredenciaisInvalidasError };
