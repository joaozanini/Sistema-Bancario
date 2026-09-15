const test = require('node:test');
const assert = require('node:assert/strict');

process.env.JWT_SECRET = 'segredo-de-teste';
const { signToken, verifyToken } = require('./token.service');

test('assina e verifica um token válido', () => {
  const { token, jti } = signToken({ cpf: '12912861012', tipo: 'CLIENTE' });
  const payload = verifyToken(token);

  assert.equal(payload.cpf, '12912861012');
  assert.equal(payload.tipo, 'CLIENTE');
  assert.equal(payload.jti, jti);
});

test('rejeita token adulterado', () => {
  const { token } = signToken({ cpf: '12912861012', tipo: 'CLIENTE' });
  const adulterado = token.slice(0, -1) + (token.at(-1) === 'a' ? 'b' : 'a');

  assert.throws(() => verifyToken(adulterado));
});
