// Pré-carregado em cada processo de teste (node --test --import).
//
// O cliente Redis conecta ao carregar config/redis.js e fica em laço de
// reconexão, segurando o event loop: sem fechá-lo, qualquer arquivo que importe
// (direto ou transitivamente) um módulo que usa Redis nunca termina.
// Registrar aqui evita que cada teste novo precise lembrar disso.
const test = require('node:test');

test.after(async () => {
  const caminho = require.resolve('./src/config/redis');
  // só fecha se o arquivo de teste realmente carregou o Redis
  if (!require.cache[caminho]) {
    return;
  }
  try {
    await require(caminho).destroy();
  } catch {
    // o próprio teste já pode ter fechado; fechar de novo não é problema
  }
});
