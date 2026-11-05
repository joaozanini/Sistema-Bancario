const test = require('node:test');
const assert = require('node:assert/strict');
const { reescreverLinks, reescreverHref, baseUrl } = require('./links.service');

const BASE = 'http://localhost:3000';

test('troca o host interno do MS pelo do Gateway e mantém caminho e query', () => {
  assert.equal(
    reescreverHref('http://localhost:8080/contas/1291/extrato?inicio=2026-01-01', BASE),
    'http://localhost:3000/contas/1291/extrato?inicio=2026-01-01',
  );
  assert.equal(reescreverHref('http://ms-conta:8080/contas/1291', BASE), 'http://localhost:3000/contas/1291');
});

test('href relativo ganha o endereço do Gateway', () => {
  assert.equal(reescreverHref('/gerentes/98574307084', BASE), 'http://localhost:3000/gerentes/98574307084');
});

test('valor que não é href reconhecível fica como está', () => {
  assert.equal(reescreverHref(undefined, BASE), undefined);
  assert.equal(reescreverHref('texto-solto', BASE), 'texto-solto');
});

test('reescreve _links em objetos, listas e níveis aninhados sem tocar nos outros campos', () => {
  const corpo = {
    solicitacoes: [
      { id: 'a', nome: 'http://localhost:8082/nao-e-link', _links: { self: { href: 'http://localhost:8082/solicitacoes/a' } } },
    ],
    resumo: { _links: { gerentes: { href: 'http://localhost:8083/gerentes' } } },
  };

  const reescrito = reescreverLinks(corpo, BASE);

  assert.equal(reescrito.solicitacoes[0]._links.self.href, 'http://localhost:3000/solicitacoes/a');
  assert.equal(reescrito.solicitacoes[0].nome, 'http://localhost:8082/nao-e-link');
  assert.equal(reescrito.resumo._links.gerentes.href, 'http://localhost:3000/gerentes');
  assert.equal(corpo.solicitacoes[0]._links.self.href, 'http://localhost:8082/solicitacoes/a', 'não altera o original');
});

test('corpo sem _links, nulo ou primitivo passa intacto', () => {
  assert.deepEqual(reescreverLinks({ a: 1, b: [1, 2] }, BASE), { a: 1, b: [1, 2] });
  assert.equal(reescreverLinks(null, BASE), null);
  assert.equal(reescreverLinks('x', BASE), 'x');
});

test('base vem do host da requisição, ou de GATEWAY_PUBLIC_URL quando definida', () => {
  const req = { headers: { host: 'localhost:3000' } };
  assert.equal(baseUrl(req), 'http://localhost:3000');
  assert.equal(baseUrl({ headers: { host: 'bantads.local', 'x-forwarded-proto': 'https' } }), 'https://bantads.local');

  process.env.GATEWAY_PUBLIC_URL = 'https://api.bantads.com.br/';
  try {
    assert.equal(baseUrl(req), 'https://api.bantads.com.br');
  } finally {
    delete process.env.GATEWAY_PUBLIC_URL;
  }
});
