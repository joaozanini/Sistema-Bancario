const jwt = require('jsonwebtoken');
const { randomUUID } = require('node:crypto');

// chave e validade do JWT lidas do ambiente
const JWT_SECRET = process.env.JWT_SECRET;
const JWT_EXPIRES_IN = process.env.JWT_EXPIRES_IN || '8h';

if (!JWT_SECRET) {
  throw new Error('JWT_SECRET não definido. Preencha a chave no .env (modelo em .env.example).');
}

// assina o JWT com jti e expiração absoluta
function signToken(payload) {
  const jti = randomUUID();
  const token = jwt.sign(payload, JWT_SECRET, { expiresIn: JWT_EXPIRES_IN, jwtid: jti });
  return { token, jti };
}

function verifyToken(token) {
  return jwt.verify(token, JWT_SECRET);
}

module.exports = { signToken, verifyToken };
