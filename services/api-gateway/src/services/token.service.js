const jwt = require('jsonwebtoken');
const { randomUUID } = require('node:crypto');

const JWT_SECRET = process.env.JWT_SECRET || 'dev-secret-troque-no-env';
const JWT_EXPIRES_IN = process.env.JWT_EXPIRES_IN || '8h';

if (!process.env.JWT_SECRET) {
  console.warn('JWT_SECRET não definido — usando valor de desenvolvimento. Configure no .env para produção.');
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
