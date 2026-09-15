const { createClient } = require('redis');

const client = createClient({
  socket: {
    host: process.env.REDIS_HOST || 'localhost',
    port: Number(process.env.REDIS_PORT) || 6379,
  },
  password: process.env.REDIS_PASSWORD || undefined,
});

client.on('error', (err) => console.error('Erro na conexão com o Redis:', err.message));
client.connect().catch((err) => console.error('Falha ao conectar no Redis:', err.message));

module.exports = client;
