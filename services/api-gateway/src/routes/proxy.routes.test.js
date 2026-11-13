const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');

const PORTA_MS_CONTA = 18080;
const PORTA_MS_CLIENTE = 18087;
process.env.MS_CONTA_URL = `http://localhost:${PORTA_MS_CONTA}`;
process.env.MS_CLIENTE_URL = `http://localhost:${PORTA_MS_CLIENTE}`;

const sessionService = require('../services/session.service');
const tokenService = require('../services/token.service');
const cache = require('../services/cache.service');
const app = require('../app');

sessionService.isRevoked = async () => false;
sessionService.getSession = async () => ({});
sessionService.renewSession = async () => {};
// sem Redis nos testes: o cache-aside vai direto à origem
cache.lerOuBuscar = async (dominio, cpf, buscarNaOrigem) => buscarNaOrigem();

const CLI1 = '12912861012';
const CLI2 = '09506382000';
const GER1 = '98574307084';

const CONTAS = {
  1291: { numero: '1291', cpfCliente: CLI1, cpfGerente: GER1, saldo: '800.00' },
  '0950': { numero: '0950', cpfCliente: CLI2, cpfGerente: GER1, saldo: '10000.00' },
};
const CLIENTES = {
  [CLI1]: { cpf: CLI1, nome: 'Catharyna' },
  [CLI2]: { cpf: CLI2, nome: 'Cleuddônio' },
};

function lerCorpo(req) {
  return new Promise((pronto) => {
    const partes = [];
    req.on('data', (parte) => partes.push(parte));
    req.on('end', () => pronto(Buffer.concat(partes).toString('utf8')));
  });
}

// MS Conta de mentira: consulta de conta de verdade (o Gateway depende dela na
// transferência), dois erros com formatos diferentes e, no resto, um eco do que chegou
const msConta = http.createServer(async (req, res) => {
  const corpo = await lerCorpo(req);
  res.setHeader('Content-Type', 'application/json');

  const consulta = req.method === 'GET' && req.url.match(/^\/contas\/(\d{4})$/);
  if (consulta) {
    const conta = CONTAS[consulta[1]];
    if (!conta) {
      res.statusCode = 404;
      return res.end(JSON.stringify({ erro: `Conta não encontrada: ${consulta[1]}` }));
    }
    return res.end(JSON.stringify(conta));
  }
  if (req.url === '/contas/1291/saque') {
    res.statusCode = 422;
    return res.end(JSON.stringify({ erro: 'Saldo insuficiente para realizar o saque.' }));
  }
  if (req.url.startsWith('/contas/1291/extrato')) {
    // erro sem corpo, como o ResponseEntity.notFound().build() do Spring
    res.removeHeader('Content-Type');
    res.statusCode = 404;
    return res.end();
  }

  res.statusCode = req.method === 'POST' ? 201 : 200;
  return res.end(
    JSON.stringify({
      caminho: req.url,
      cpf: req.headers['x-user-cpf'] ?? null,
      tipo: req.headers['x-user-tipo'] ?? null,
      corpo: corpo ? JSON.parse(corpo) : null,
    }),
  );
});

const msCliente = http.createServer((req, res) => {
  const cliente = CLIENTES[req.url.replace('/clientes/', '')];
  res.setHeader('Content-Type', 'application/json');
  if (!cliente) {
    res.statusCode = 404;
    return res.end();
  }
  return res.end(JSON.stringify(cliente));
});
let gateway;

test.before(async () => {
  await new Promise((pronto) => msConta.listen(PORTA_MS_CONTA, pronto));
  await new Promise((pronto) => msCliente.listen(PORTA_MS_CLIENTE, pronto));
  await new Promise((pronto) => {
    gateway = app.listen(0, pronto);
  });
});

test.after(() => {
  gateway.closeAllConnections();
  gateway.close();
  msConta.closeAllConnections();
  msConta.close();
  msCliente.closeAllConnections();
  msCliente.close();
});

function chamar(caminho, { usuario, metodo = 'GET', corpo, headers = {} } = {}) {
  const cabecalhos = { ...headers };
  if (usuario) {
    cabecalhos['x-access-token'] = tokenService.signToken(usuario).token;
  }
  if (corpo !== undefined) {
    cabecalhos['Content-Type'] = 'application/json';
  }
  return fetch(`http://localhost:${gateway.address().port}${caminho}`, {
    method: metodo,
    headers: cabecalhos,
    body: corpo === undefined ? undefined : typeof corpo === 'string' ? corpo : JSON.stringify(corpo),
  });
}

const cliente1 = { cpf: CLI1, tipo: 'CLIENTE' };
const gerente1 = { cpf: GER1, tipo: 'GERENTE' };

test('consulta de conta pelo numero e repassada com a identidade do token', async () => {
  const resposta = await chamar('/contas/1291', { usuario: cliente1 });

  assert.equal(resposta.status, 200);
  assert.equal((await resposta.json()).numero, '1291');
});

test('a listagem de contas nao faz parte do contrato e nao e exposta', async () => {
  const resposta = await chamar('/contas', { usuario: gerente1 });

  assert.equal(resposta.status, 404);
  assert.deepEqual(await resposta.json(), { status: 404, erro: 'Not Found', mensagem: 'Rota não encontrada.' });
});

test('conta do cliente (R3) e atendida pelo MS Conta, que recebe quem esta consultando', async () => {
  const resposta = await chamar(`/clientes/${CLI1}/conta`, { usuario: cliente1 });

  assert.equal(resposta.status, 200);
  assert.deepEqual(await resposta.json(), {
    caminho: `/clientes/${CLI1}/conta`,
    cpf: CLI1,
    tipo: 'CLIENTE',
    corpo: null,
  });
});

test('identidade forjada nos headers nao chega ao MS', async () => {
  const resposta = await chamar(`/clientes/${CLI2}/conta`, {
    usuario: cliente1,
    headers: { 'X-User-CPF': CLI2, 'X-User-Tipo': 'GERENTE' },
  });

  const { cpf, tipo } = await resposta.json();
  assert.equal(cpf, CLI1);
  assert.equal(tipo, 'CLIENTE');
});

test('deposito do cliente chega ao MS Conta com o corpo e o X-User-CPF', async () => {
  const resposta = await chamar('/contas/1291/deposito', {
    usuario: cliente1,
    metodo: 'POST',
    corpo: { valor: '150.00' },
  });

  assert.equal(resposta.status, 201);
  assert.deepEqual(await resposta.json(), {
    caminho: '/contas/1291/deposito',
    cpf: CLI1,
    tipo: 'CLIENTE',
    corpo: { valor: '150.00' },
  });
});

test('gerente nao deposita, nao saca e nao transfere: 403 antes de chegar ao MS', async () => {
  for (const operacao of ['deposito', 'saque', 'transferencia']) {
    const resposta = await chamar(`/contas/1291/${operacao}`, {
      usuario: gerente1,
      metodo: 'POST',
      corpo: { valor: '10.00', contaDestino: '0950' },
    });

    assert.equal(resposta.status, 403, operacao);
    const corpo = await resposta.json();
    assert.equal(corpo.status, 403);
    assert.equal(corpo.erro, 'Forbidden');
    assert.ok(corpo.mensagem);
  }
});

test('gerente consulta o extrato; a rota e repassada com o perfil dele', async () => {
  const resposta = await chamar('/contas/0950/extrato?inicio=2025-01-01&fim=2025-01-31', { usuario: gerente1 });

  assert.equal(resposta.status, 200);
  const corpo = await resposta.json();
  assert.equal(corpo.caminho, '/contas/0950/extrato?inicio=2025-01-01&fim=2025-01-31');
  assert.equal(corpo.tipo, 'GERENTE');
});

test('erro do MS sai no formato do contrato: { status, erro, mensagem }', async () => {
  const resposta = await chamar('/contas/1291/saque', {
    usuario: cliente1,
    metodo: 'POST',
    corpo: { valor: '99999999.00' },
  });

  assert.equal(resposta.status, 422);
  assert.deepEqual(await resposta.json(), {
    status: 422,
    erro: 'Unprocessable Entity',
    mensagem: 'Saldo insuficiente para realizar o saque.',
  });
});

test('erro do MS sem corpo tambem ganha o formato do contrato', async () => {
  const resposta = await chamar('/contas/1291/extrato', { usuario: cliente1 });

  assert.equal(resposta.status, 404);
  assert.match(resposta.headers.get('content-type'), /application\/json/);
  assert.deepEqual(await resposta.json(), { status: 404, erro: 'Not Found', mensagem: 'Recurso não encontrado.' });
});

test('transferencia chega ao MS Conta enriquecida com CPFs e nomes (R6)', async () => {
  const resposta = await chamar('/contas/1291/transferencia', {
    usuario: cliente1,
    metodo: 'POST',
    corpo: { contaDestino: '0950', valor: '30.00' },
  });

  assert.equal(resposta.status, 201);
  assert.deepEqual((await resposta.json()).corpo, {
    contaDestino: '0950',
    valor: '30.00',
    cpfDestino: CLI2,
    cpfOrigem: CLI1,
    nomeOrigem: 'Catharyna',
    nomeDestino: 'Cleuddônio',
  });
});

test('transferencia para conta inexistente e regra de negocio: 422', async () => {
  const resposta = await chamar('/contas/1291/transferencia', {
    usuario: cliente1,
    metodo: 'POST',
    corpo: { contaDestino: '9999', valor: '10.00' },
  });

  assert.equal(resposta.status, 422);
  assert.deepEqual(await resposta.json(), {
    status: 422,
    erro: 'Unprocessable Entity',
    mensagem: 'Conta destino não encontrada.',
  });
});

test('transferencia a partir da conta de outro cliente e 403, exista ou nao a conta destino', async () => {
  for (const contaDestino of ['1291', '9999']) {
    const resposta = await chamar('/contas/0950/transferencia', {
      usuario: cliente1,
      metodo: 'POST',
      corpo: { contaDestino, valor: '10.00' },
    });

    assert.equal(resposta.status, 403, `destino ${contaDestino}`);
  }
});

test('transferencia sem conta destino e requisicao malformada: 400', async () => {
  const resposta = await chamar('/contas/1291/transferencia', {
    usuario: cliente1,
    metodo: 'POST',
    corpo: { valor: '10.00' },
  });

  assert.equal(resposta.status, 400);
  assert.equal((await resposta.json()).erro, 'Bad Request');
});

test('corpo com JSON invalido responde 400 no formato do contrato', async () => {
  const resposta = await chamar('/contas/1291/deposito', {
    usuario: cliente1,
    metodo: 'POST',
    corpo: '{"valor": ',
  });

  assert.equal(resposta.status, 400);
  const corpo = await resposta.json();
  assert.equal(corpo.status, 400);
  assert.equal(corpo.erro, 'Bad Request');
});

test('health responde JSON com status UP', async () => {
  const resposta = await chamar('/health');

  assert.equal(resposta.status, 200);
  assert.deepEqual(await resposta.json(), { status: 'UP' });
});

test('logout responde 204 sem corpo e encerra a sessao do token', async () => {
  const encerradas = [];
  const original = sessionService.revokeSession;
  sessionService.revokeSession = async (jti, cpf) => {
    encerradas.push(cpf);
  };

  try {
    const resposta = await chamar('/logout', { usuario: gerente1, metodo: 'POST' });

    assert.equal(resposta.status, 204);
    assert.equal(await resposta.text(), '');
    assert.deepEqual(encerradas, [GER1]);
  } finally {
    sessionService.revokeSession = original;
  }
});

test('login e logout so existem nos caminhos do contrato', async () => {
  const login = await chamar('/auth/login', { metodo: 'POST', corpo: { email: 'a@b.c', senha: 'x' } });
  const logout = await chamar('/auth/logout', { usuario: gerente1, metodo: 'POST' });

  assert.equal(login.status, 404);
  assert.equal(logout.status, 404);
});

test('login sem e-mail ou senha responde 400 no formato do contrato', async () => {
  const resposta = await chamar('/login', { metodo: 'POST', corpo: { email: 'cli1@bantads.com.br' } });

  assert.equal(resposta.status, 400);
  assert.deepEqual(await resposta.json(), {
    status: 400,
    erro: 'Bad Request',
    mensagem: 'E-mail e senha são obrigatórios.',
  });
});

// fica por último: derruba o MS Conta de mentira
test('MS fora do ar responde 502 no formato do contrato', async () => {
  msConta.closeAllConnections();
  await new Promise((fechado) => msConta.close(fechado));
  try {
    const resposta = await chamar('/contas/1291', { usuario: cliente1 });

    assert.equal(resposta.status, 502);
    assert.deepEqual(await resposta.json(), { status: 502, erro: 'Bad Gateway', mensagem: 'Serviço indisponível.' });
  } finally {
    await new Promise((pronto) => msConta.listen(PORTA_MS_CONTA, pronto));
  }
});
