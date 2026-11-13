const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');

const PORTA_MS_CLIENTE = 18082;
process.env.MS_CLIENTE_URL = `http://localhost:${PORTA_MS_CLIENTE}`;

const sessionService = require('../services/session.service');
const tokenService = require('../services/token.service');
const app = require('../app');

sessionService.isRevoked = async () => false;
sessionService.getSession = async () => ({});
sessionService.renewSession = async () => {};

const CPF_CLIENTE = '12912861012';
const CPF_OUTRO_CLIENTE = '09506382000';
const CPF_GERENTE = '98574307084';

const cadastrados = {
  [CPF_CLIENTE]: { cpf: CPF_CLIENTE, nome: 'Catharyna', email: 'cli1@bantads.com.br' },
  [CPF_OUTRO_CLIENTE]: { cpf: CPF_OUTRO_CLIENTE, nome: 'Cleuddônio', email: 'cli2@bantads.com.br' },
};

const msCliente = http.createServer((req, res) => {
  const cliente = cadastrados[req.url.replace('/clientes/', '')];
  res.setHeader('Content-Type', 'application/json');
  if (!cliente) {
    res.statusCode = 404;
    return res.end();
  }
  return res.end(JSON.stringify(cliente));
});
let gateway;

test.before(async () => {
  await new Promise((pronto) => msCliente.listen(PORTA_MS_CLIENTE, pronto));
  await new Promise((pronto) => {
    gateway = app.listen(0, pronto);
  });
});

test.after(() => {
  gateway.closeAllConnections();
  gateway.close();
  msCliente.closeAllConnections();
  msCliente.close();
});

function chamar(caminho, usuario) {
  const headers = {};
  if (usuario) {
    headers['x-access-token'] = tokenService.signToken(usuario).token;
  }
  return fetch(`http://localhost:${gateway.address().port}${caminho}`, { headers });
}

test('gerente consulta qualquer cliente', async () => {
  const resposta = await chamar(`/clientes/${CPF_CLIENTE}`, { cpf: CPF_GERENTE, tipo: 'GERENTE' });

  assert.equal(resposta.status, 200);
  assert.deepEqual(await resposta.json(), cadastrados[CPF_CLIENTE]);
});

test('cliente consulta o proprio cadastro', async () => {
  const resposta = await chamar(`/clientes/${CPF_CLIENTE}`, { cpf: CPF_CLIENTE, tipo: 'CLIENTE' });

  assert.equal(resposta.status, 200);
  assert.equal((await resposta.json()).cpf, CPF_CLIENTE);
});

test('cliente nao consulta o cadastro de outro cliente', async () => {
  const resposta = await chamar(`/clientes/${CPF_OUTRO_CLIENTE}`, { cpf: CPF_CLIENTE, tipo: 'CLIENTE' });

  assert.equal(resposta.status, 403);
  const corpo = await resposta.json();
  assert.equal(corpo.status, 403);
  assert.equal(corpo.erro, 'Forbidden');
});

test('listagem (R11) vai dentro de { clientes } com os _links e repassa a busca', async () => {
  const clientesService = require('../services/clientes.service');
  const original = clientesService.listarTodos;
  const buscas = [];
  clientesService.listarTodos = async (busca) => {
    buscas.push(busca);
    return [{ cpf: CPF_CLIENTE, nome: 'Catharyna', cidade: 'Curitiba', estado: 'PR', saldo: '800.00' }];
  };

  try {
    const resposta = await chamar('/clientes?busca=cat', { cpf: CPF_GERENTE, tipo: 'GERENTE' });
    const corpo = await resposta.json();

    assert.equal(resposta.status, 200);
    assert.deepEqual(corpo.clientes, [
      { cpf: CPF_CLIENTE, nome: 'Catharyna', cidade: 'Curitiba', estado: 'PR', saldo: '800.00' },
    ]);
    assert.equal(corpo._links.self.href, `http://localhost:${gateway.address().port}/clientes`);
    assert.deepEqual(buscas, ['cat']);
  } finally {
    clientesService.listarTodos = original;
  }
});

test('cliente nao lista os clientes do banco', async () => {
  const resposta = await chamar('/clientes', { cpf: CPF_CLIENTE, tipo: 'CLIENTE' });

  assert.equal(resposta.status, 403);
});

test('cpf inexistente devolve 404', async () => {
  const resposta = await chamar('/clientes/00000000000', { cpf: CPF_GERENTE, tipo: 'GERENTE' });

  assert.equal(resposta.status, 404);
  assert.deepEqual(await resposta.json(), { status: 404, erro: 'Not Found', mensagem: 'Cliente não encontrado.' });
});

test('sem token devolve 401', async () => {
  const resposta = await chamar(`/clientes/${CPF_CLIENTE}`);

  assert.equal(resposta.status, 401);
  assert.deepEqual(await resposta.json(), { auth: false, message: 'Token não fornecido.' });
});

test('MS Cliente fora do ar devolve 502', async () => {
  msCliente.closeAllConnections();
  await new Promise((fechado) => msCliente.close(fechado));
  try {
    const resposta = await chamar(`/clientes/${CPF_CLIENTE}`, { cpf: CPF_GERENTE, tipo: 'GERENTE' });

    assert.equal(resposta.status, 502);
  } finally {
    await new Promise((pronto) => msCliente.listen(PORTA_MS_CLIENTE, pronto));
  }
});
