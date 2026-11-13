const test = require('node:test');
const assert = require('node:assert/strict');

const { exigirTipo, descartarIdentidadeDoCliente, injectUserHeaders } = require('./auth.middleware');

function respostaFalsa() {
  return {
    status(codigo) {
      this.statusCode = codigo;
      return this;
    },
    json(corpo) {
      this.body = corpo;
      return this;
    },
  };
}

test('deixa passar o usuario do tipo exigido', () => {
  const res = respostaFalsa();
  let passou = false;

  exigirTipo('GERENTE')({ user: { tipo: 'GERENTE' } }, res, () => {
    passou = true;
  });

  assert.ok(passou);
  assert.equal(res.statusCode, undefined);
});

test('responde 403 para usuario de outro tipo', () => {
  const res = respostaFalsa();
  let passou = false;

  exigirTipo('GERENTE')({ user: { tipo: 'CLIENTE' } }, res, () => {
    passou = true;
  });

  assert.ok(!passou);
  assert.equal(res.statusCode, 403);
  // corpo de erro do contrato: { status, erro, mensagem }
  assert.equal(res.body.status, 403);
  assert.equal(res.body.erro, 'Forbidden');
  assert.match(res.body.mensagem, /GERENTE/);
});

test('identidade enviada pelo cliente e descartada e so a do token chega ao MS', () => {
  const req = {
    headers: { 'x-user-cpf': '09506382000', 'x-user-tipo': 'GERENTE', 'x-access-token': 'abc' },
    user: { cpf: '12912861012', tipo: 'CLIENTE' },
  };

  descartarIdentidadeDoCliente(req, {}, () => {});
  assert.deepEqual(req.headers, { 'x-access-token': 'abc' });

  injectUserHeaders(req, {}, () => {});
  assert.equal(req.headers['x-user-cpf'], '12912861012');
  assert.equal(req.headers['x-user-tipo'], 'CLIENTE');
});
