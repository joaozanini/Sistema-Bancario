const redis = require('../config/redis');

const CACHE_TTL_SECONDS = 5 * 60;

// Só dados CADASTRAIS entram aqui. Saldo, extrato e contagem de clientes vêm do
// MS Conta, que é de consistência eventual (CQRS) — cachear esconderia a defasagem.
const DOMINIOS = { CLIENTE: 'cliente', GERENTE: 'gerente' };

const chave = (dominio, cpf) => `cache:${dominio}:${cpf}`;
const expiraEm = (segundos) => ({ expiration: { type: 'EX', value: segundos } });

// Cache-aside: lê do cache, e só vai à origem quando não achou.
// Falha de Redis nunca derruba a requisição — degrada para a origem.
async function lerOuBuscar(dominio, cpf, buscarNaOrigem) {
  try {
    const bruto = await redis.get(chave(dominio, cpf));
    if (bruto) {
      return JSON.parse(bruto);
    }
  } catch (err) {
    console.error('Cache indisponível na leitura:', err.message);
    return buscarNaOrigem();
  }

  const dados = await buscarNaOrigem();

  try {
    await redis.set(chave(dominio, cpf), JSON.stringify(dados), expiraEm(CACHE_TTL_SECONDS));
  } catch (err) {
    console.error('Cache indisponível na escrita:', err.message);
  }

  return dados;
}

async function invalidar(dominio, cpf) {
  try {
    await redis.del(chave(dominio, cpf));
  } catch (err) {
    console.error('Cache indisponível na invalidação:', err.message);
  }
}

module.exports = { lerOuBuscar, invalidar, DOMINIOS, CACHE_TTL_SECONDS };
