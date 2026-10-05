const amqp = require('amqplib');

const url = `amqp://${process.env.RABBITMQ_USER || 'admin'}:${process.env.RABBITMQ_PASSWORD || ''}`
  + `@${process.env.RABBITMQ_HOST || 'localhost'}:${process.env.RABBITMQ_PORT || 5672}`;

let canalPromise = null;

// Canal com confirmação: perder uma publicação em saga.cmd significa perder a SAGA
// inteira, então esperamos o broker confirmar antes de responder 202 ao cliente.
function abrirCanal() {
  return amqp.connect(url).then((conexao) => {
    conexao.on('error', (err) => console.error('Erro na conexão com o RabbitMQ:', err.message));
    conexao.on('close', () => {
      canalPromise = null;
    });
    return conexao.createConfirmChannel();
  });
}

function canal() {
  if (!canalPromise) {
    // zera em caso de falha para a próxima publicação tentar reconectar
    canalPromise = abrirCanal().catch((err) => {
      canalPromise = null;
      throw err;
    });
  }
  return canalPromise;
}

async function publicar(fila, mensagem) {
  const ch = await canal();
  ch.sendToQueue(fila, Buffer.from(JSON.stringify(mensagem)), { persistent: true });
  await ch.waitForConfirms();
}

module.exports = { publicar };
