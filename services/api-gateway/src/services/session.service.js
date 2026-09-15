const redis = require('../config/redis');

const SESSION_TTL_SECONDS = 30 * 60; // timeout de inatividade (sliding window), fixo pelo enunciado

const sessionKey = (jti) => `sessao:${jti}`;
const reverseKey = (cpf) => `sessao:cpf:${cpf}`;
const revokedKey = (jti) => `sessao:revogado:${jti}`;

const expiraEm = (segundos) => ({ expiration: { type: 'EX', value: segundos } });

async function createSession(jti, cpf, tipo) {
  await redis.set(sessionKey(jti), JSON.stringify({ cpf, tipo }), expiraEm(SESSION_TTL_SECONDS));
  await redis.set(reverseKey(cpf), jti, expiraEm(SESSION_TTL_SECONDS));
}

async function getSession(jti) {
  const raw = await redis.get(sessionKey(jti));
  return raw ? JSON.parse(raw) : null;
}

async function renewSession(jti, cpf) {
  await redis.expire(sessionKey(jti), SESSION_TTL_SECONDS);
  await redis.expire(reverseKey(cpf), SESSION_TTL_SECONDS);
}

async function isRevoked(jti) {
  return (await redis.exists(revokedKey(jti))) === 1;
}

// remainingTokenSeconds = tempo restante até o exp do JWT (TTL do revogado, per enunciado)
async function revokeSession(jti, cpf, remainingTokenSeconds) {
  await redis.del(sessionKey(jti));
  await redis.del(reverseKey(cpf));
  if (remainingTokenSeconds > 0) {
    await redis.set(revokedKey(jti), '1', expiraEm(remainingTokenSeconds));
  }
}

module.exports = { createSession, getSession, renewSession, isRevoked, revokeSession };
