const test = require('node:test');
const assert = require('node:assert/strict');

const { exigirTipo } = require('./auth.middleware');
const redis = require('../config/redis');

test.after(() => redis.destroy());

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
  assert.deepEqual(res.body, { message: 'Acesso negado.' });
});
