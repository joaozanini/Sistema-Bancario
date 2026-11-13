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

test('sem JWT_SECRET o módulo não carrega (não existe chave reserva no código)', () => {
  const caminho = require.resolve('./token.service');
  const moduloEmCache = require.cache[caminho];
  const chave = process.env.JWT_SECRET;
  delete require.cache[caminho];
  delete process.env.JWT_SECRET;

  try {
    assert.throws(() => require('./token.service'), /JWT_SECRET não definido/);
  } finally {
    process.env.JWT_SECRET = chave;
    require.cache[caminho] = moduloEmCache;
  }
});
