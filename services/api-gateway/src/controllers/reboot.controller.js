const {
  authServiceUrl,
  clienteServiceUrl,
  contaServiceUrl,
  gerenteServiceUrl,
} = require('../config/services');
const redis = require('../config/redis');
const clientesService = require('../services/clientes.service');
const contaService = require('../services/conta.service');
const gerenteService = require('../services/gerente.service');

const SERVICOS = [
  { nome: 'cliente', url: clienteServiceUrl },
  { nome: 'conta', url: contaServiceUrl },
  { nome: 'gerente', url: gerenteServiceUrl },
  { nome: 'auth', url: authServiceUrl },
];

// Sessões e jobs são do Gateway; o cache também. Nada disso pode sobreviver a um
// reboot, senão a correção parte de um estado que não é o do seed.
const PADROES_REDIS = ['sessao:*', 'job:*', 'cache:*'];

async function resetar({ nome, url }) {
  let resposta;
  try {
    resposta = await fetch(`${url}/reset`, { method: 'POST' });
  } catch (err) {
    return { nome, ok: false, motivo: err.message };
  }

  // 404 = o serviço ainda não expõe /reset. Não é falha de reboot: o seed dele
  // nasce no boot. Fica no resumo para ficar visível.
  if (resposta.status === 404) {
    return { nome, ok: true, semReset: true };
  }
  if (!resposta.ok) {
    return { nome, ok: false, motivo: `respondeu ${resposta.status}` };
  }
  return { nome, ok: true };
}

async function limparRedis() {
  for (const padrao of PADROES_REDIS) {
    // KEYS varre tudo, mas o reboot roda num banco de teste com poucas chaves e
    // fora do caminho das requisições — não vale a complexidade do SCAN aqui.
    const chaves = await redis.keys(padrao);
    if (chaves.length > 0) {
      await redis.del(chaves);
    }
  }
}

async function contar() {
  const [clientes, gerentes, contas] = await Promise.all([
    clientesService.listarTodos(),
    gerenteService.listarAtivos(),
    contaService.listar(),
  ]);
  return { clientes: clientes.length, gerentes: gerentes.length, contas: contas.length };
}

async function reboot(req, res) {
  const resultados = await Promise.all(SERVICOS.map(resetar));
  const falhas = resultados.filter((r) => !r.ok);

  if (falhas.length > 0) {
    console.error('Reboot falhou:', falhas.map((f) => `${f.nome}: ${f.motivo}`).join('; '));
    return res.status(502).json({
      status: 'erro',
      message: 'Não foi possível recriar o seed em todos os serviços.',
      servicos: Object.fromEntries(resultados.map((r) => [r.nome, r.ok ? 'ok' : r.motivo])),
    });
  }

  try {
    await limparRedis();
  } catch (err) {
    console.error('Reboot falhou ao limpar o Redis:', err.message);
    return res.status(503).json({ status: 'erro', message: 'Sessão indisponível.' });
  }

  let contagens;
  try {
    contagens = await contar();
  } catch (err) {
    // O seed foi recriado; só a contagem falhou. Devolver 200 esconderia isso.
    console.error('Reboot recriou o seed mas falhou ao contar:', err.message);
    return res.status(502).json({ status: 'erro', message: 'Seed recriado, mas a contagem falhou.' });
  }

  const semReset = resultados.filter((r) => r.semReset).map((r) => r.nome);
  return res.json({ status: 'ok', ...contagens, ...(semReset.length > 0 ? { semReset } : {}) });
}

module.exports = { reboot };
