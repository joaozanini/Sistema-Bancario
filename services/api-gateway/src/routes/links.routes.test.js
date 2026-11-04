const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');

const PORTA_MS_CONTA = 18084;
const PORTA_MS_GERENTE = 18085;
const PORTA_MS_CLIENTE = 18086;
process.env.MS_CONTA_URL = `http://localhost:${PORTA_MS_CONTA}`;
process.env.MS_GERENTE_URL = `http://localhost:${PORTA_MS_GERENTE}`;
process.env.MS_CLIENTE_URL = `http://localhost:${PORTA_MS_CLIENTE}`;

const sessionService = require('../services/session.service');
const tokenService = require('../services/token.service');
const app = require('../app');

sessionService.isRevoked = async () => false;
sessionService.getSession = async () => ({});
sessionService.renewSession = async () => {};

function msFalso(porta, responder) {
  return http.createServer((req, res) => responder(req, res));
}

// o MS Conta devolve links com o endereço interno dele, como faz hoje com localhost:8080
const msConta = msFalso(PORTA_MS_CONTA, (req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify({
    numero: '1291',
    _links: {
      self: { href: 'http://localhost:8080/contas/1291' },
      deposito: { href: 'http://localhost:8080/contas/1291/deposito' },
    },
  }));
});
const msGerente = msFalso(PORTA_MS_GERENTE, (req, res) => {
  res.setHeader('Content-Type', 'application/json');
  return res.end(JSON.stringify({ cpf: '98574307084', _links: { self: { href: 'http://localhost:8083/gerentes/98574307084' } } }));
});
// rota de composição (resposta montada pelo Gateway, não repassada pelo proxy)
const msCliente = msFalso(PORTA_MS_CLIENTE, (req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify({
    cpf: '12912861012',
    nome: 'Catharyna',
    _links: { self: { href: 'http://localhost:8082/clientes/12912861012' } },
  }));
});
let gateway;

test.before(async () => {
  await new Promise((pronto) => msConta.listen(PORTA_MS_CONTA, pronto));
  await new Promise((pronto) => msGerente.listen(PORTA_MS_GERENTE, pronto));
  await new Promise((pronto) => msCliente.listen(PORTA_MS_CLIENTE, pronto));
  await new Promise((pronto) => {
    gateway = app.listen(0, pronto);
  });
});

test.after(() => {
  gateway.closeAllConnections();
  gateway.close();
  msConta.closeAllConnections();
  msConta.close();
  msGerente.closeAllConnections();
  msGerente.close();
  msCliente.closeAllConnections();
  msCliente.close();
});

function chamar(caminho, usuario) {
  const headers = { 'x-access-token': tokenService.signToken(usuario).token };
  return fetch(`http://localhost:${gateway.address().port}${caminho}`, { headers });
}

test('links do MS Conta repassados pelo proxy apontam para o Gateway', async () => {
  const resposta = await chamar('/contas/1291', { cpf: '12912861012', tipo: 'CLIENTE' });
  const corpo = await resposta.json();
  const base = `http://localhost:${gateway.address().port}`;

  assert.equal(resposta.status, 200);
  assert.equal(corpo._links.self.href, `${base}/contas/1291`);
  assert.equal(corpo._links.deposito.href, `${base}/contas/1291/deposito`);
});

test('PUT de gerente, que também passa pelo proxy, tem os links reescritos', async () => {
  const resposta = await fetch(`http://localhost:${gateway.address().port}/gerentes/98574307084`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'x-access-token': tokenService.signToken({ cpf: '98574307084', tipo: 'GERENTE' }).token,
    },
    body: JSON.stringify({ nome: 'Geniéve' }),
  });
  const corpo = await resposta.json();

  assert.equal(corpo._links.self.href, `http://localhost:${gateway.address().port}/gerentes/98574307084`);
});

test('GET /clientes/:cpf, montado pelo Gateway, também devolve links do Gateway', async () => {
  const resposta = await chamar('/clientes/12912861012', { cpf: '98574307084', tipo: 'GERENTE' });
  const corpo = await resposta.json();

  assert.equal(resposta.status, 200);
  assert.equal(corpo._links.self.href, `http://localhost:${gateway.address().port}/clientes/12912861012`);
});
