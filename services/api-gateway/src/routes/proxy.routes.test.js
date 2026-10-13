const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');

const PORTA_MS_CONTA = 18080;
process.env.MS_CONTA_URL = `http://localhost:${PORTA_MS_CONTA}`;

const redis = require('../config/redis');
const sessionService = require('../services/session.service');
const tokenService = require('../services/token.service');
const app = require('../app');

sessionService.isRevoked = async () => false;
sessionService.getSession = async () => ({});
sessionService.renewSession = async () => {};

const msConta = http.createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.end(JSON.stringify({ caminho: req.url }));
});
let gateway;

test.before(async () => {
  await new Promise((pronto) => msConta.listen(PORTA_MS_CONTA, pronto));
  await new Promise((pronto) => {
    gateway = app.listen(0, pronto);
  });
});

test.after(() => {
  gateway.closeAllConnections();
  gateway.close();
  msConta.closeAllConnections();
  msConta.close();
  redis.destroy();
});

function chamar(caminho, tipo) {
  const { token } = tokenService.signToken({ cpf: '12912861012', tipo });
  return fetch(`http://localhost:${gateway.address().port}${caminho}`, {
    headers: { 'x-access-token': token },
  });
}

test('cliente nao lista as contas do banco', async () => {
  const resposta = await chamar('/contas', 'CLIENTE');

  assert.equal(resposta.status, 403);
});

test('gerente lista as contas do banco', async () => {
  const resposta = await chamar('/contas', 'GERENTE');

  assert.equal(resposta.status, 200);
  assert.deepEqual(await resposta.json(), { caminho: '/contas' });
});

test('cliente continua consultando uma conta pelo numero', async () => {
  const resposta = await chamar('/contas/1291', 'CLIENTE');

  assert.equal(resposta.status, 200);
  assert.deepEqual(await resposta.json(), { caminho: '/contas/1291' });
});
