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
const { corpoErro, responderErro } = require('../services/erro.service');

const SERVICOS = [
  { nome: 'cliente', url: clienteServiceUrl },
  { nome: 'conta', url: contaServiceUrl },
  { nome: 'gerente', url: gerenteServiceUrl },
  { nome: 'auth', url: authServiceUrl },
];

// padrões de chaves do Redis apagados no reboot
const PADROES_REDIS = ['sessao:*', 'job:*', 'cache:*', 'saga:*'];

async function resetar({ nome, url }) {
  let resposta;
  try {
    resposta = await fetch(`${url}/reset`, { method: 'POST' });
  } catch (err) {
    return { nome, ok: false, motivo: err.message };
  }

  if (!resposta.ok) {
    return { nome, ok: false, motivo: `respondeu ${resposta.status}` };
  }
  return { nome, ok: true };
}

async function limparRedis() {
  for (const padrao of PADROES_REDIS) {
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
    // inclui no erro o resultado de cada serviço
    return res.status(502).json({
      ...corpoErro(502, 'Não foi possível recriar o seed em todos os serviços.'),
      servicos: Object.fromEntries(resultados.map((r) => [r.nome, r.ok ? 'ok' : r.motivo])),
    });
  }

  try {
    await limparRedis();
  } catch (err) {
    console.error('Reboot falhou ao limpar o Redis:', err.message);
    return responderErro(res, 503, 'Sessão indisponível.');
  }

  let contagens;
  try {
    contagens = await contar();
  } catch (err) {
    console.error('Reboot recriou o seed mas falhou ao contar:', err.message);
    return responderErro(res, 502, 'Seed recriado, mas a contagem falhou.');
  }

  return res.json({ status: 'ok', ...contagens });
}

module.exports = { reboot };
