const jwt = require('jsonwebtoken');
const { randomUUID } = require('node:crypto');

// A chave de assinatura vem só do ambiente (.env). Sem ela o Gateway não sobe: um valor
// reserva no código seria uma chave conhecida por qualquer um que lê o fonte
const JWT_SECRET = process.env.JWT_SECRET;
const JWT_EXPIRES_IN = process.env.JWT_EXPIRES_IN || '8h';

if (!JWT_SECRET) {
  throw new Error('JWT_SECRET não definido. Preencha a chave no .env (modelo em .env.example).');
}

// exp do JWT = tempo de vida absoluto da sessão, não renovável (diferente do TTL do Redis, que é sliding window)
function signToken(payload) {
  const jti = randomUUID();
  const token = jwt.sign(payload, JWT_SECRET, { expiresIn: JWT_EXPIRES_IN, jwtid: jti });
  return { token, jti };
}

function verifyToken(token) {
  return jwt.verify(token, JWT_SECRET);
}

module.exports = { signToken, verifyToken };
