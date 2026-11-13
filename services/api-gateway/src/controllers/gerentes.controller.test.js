const test = require('node:test');
const assert = require('node:assert/strict');

const sagaService = require('../services/saga.service');
const redis = require('../config/redis');
const { listar, inserir, remover, TIPO_INSERIR_GERENTE, TIPO_REMOVER_GERENTE, DOMINIO, buscar } = require('./gerentes.controller');

const CPF_LOGADO = '98574307084';
const CPF_ALVO = '64065268052';

function resposta() {
  return {
    codigo: null,
    corpo: null,
    cabecalhos: {},
    status(c) {
      this.codigo = c;
      return this;
    },
    location(url) {
      this.cabecalhos.Location = url;
      return this;
    },
    json(c) {
      this.corpo = c;
      return this;
    },
  };
}

// o que o saga.service devolve ao criar o job: é o corpo do 202
function jobPendente(jobId, dominio) {
  return { jobId, status: 'PENDENTE', resultType: null, dominio, resourceId: null, erro: null };
}

function comSagaDublada(implementacao) {
  const original = sagaService.iniciar;
  sagaService.iniciar = implementacao;
  return () => {
    sagaService.iniciar = original;
  };
}

test('gerente nao pode remover a si mesmo', async () => {
  let publicou = false;
  const restaurar = comSagaDublada(async () => {
    publicou = true;
    return jobPendente('job-1', 'gerentes');
  });

  try {
    const res = resposta();
    await remover({ params: { cpf: CPF_LOGADO }, user: { cpf: CPF_LOGADO } }, res);

    assert.equal(res.codigo, 403);
    assert.deepEqual(res.corpo, {
      status: 403,
      erro: 'Forbidden',
      mensagem: 'Um gerente não pode remover a si mesmo.',
    });
    assert.equal(publicou, false, 'a SAGA nao pode nem comecar');
  } finally {
    restaurar();
  }
});

test('remover outro gerente devolve 202 com o job e o Location do polling', async () => {
  const restaurar = comSagaDublada(async () => jobPendente('job-123', 'gerentes'));
  try {
    const res = resposta();
    await remover({ params: { cpf: CPF_ALVO }, user: { cpf: CPF_LOGADO } }, res);

    assert.equal(res.codigo, 202);
    assert.deepEqual(res.corpo, jobPendente('job-123', 'gerentes'));
    assert.equal(res.cabecalhos.Location, '/jobs/job-123/status');
  } finally {
    restaurar();
  }
});

test('publica o tipo e o dominio da saga de remocao', async () => {
  const recebido = [];
  const restaurar = comSagaDublada(async (tipo, dominio, payload) => {
    recebido.push({ tipo, dominio, payload });
    return jobPendente('job-1', 'gerentes');
  });

  try {
    await remover({ params: { cpf: CPF_ALVO }, user: { cpf: CPF_LOGADO } }, resposta());

    assert.equal(recebido[0].tipo, 'remover-gerente');
    assert.equal(recebido[0].dominio, 'gerentes');
    assert.deepEqual(recebido[0].payload, { cpf: CPF_ALVO });
  } finally {
    restaurar();
  }
});

test('as constantes exportadas batem com o que e publicado', () => {
  assert.equal(TIPO_REMOVER_GERENTE, 'remover-gerente');
  assert.equal(DOMINIO, 'gerentes');
});

test('falha ao publicar vira 502 e nao 202', async () => {
  const restaurar = comSagaDublada(async () => {
    throw new Error('broker fora do ar');
  });

  try {
    const res = resposta();
    await remover({ params: { cpf: CPF_ALVO }, user: { cpf: CPF_LOGADO } }, res);

    assert.equal(res.codigo, 502);
  } finally {
    restaurar();
  }
});

const NOVO_GERENTE = {
  cpf: '11144477735',
  nome: 'Gerente Novo',
  email: 'novo.gerente@bantads.com.br',
  telefone: '41999990000',
  senha: 'senhaDoFormulario',
};

test('inserir devolve 202 com o job e o Location do polling', async () => {
  const restaurar = comSagaDublada(async () => jobPendente('job-13', 'gerentes'));
  try {
    const res = resposta();
    await inserir({ body: NOVO_GERENTE, user: { cpf: CPF_LOGADO } }, res);

    assert.equal(res.codigo, 202);
    assert.equal(res.corpo.jobId, 'job-13');
    assert.equal(res.corpo.status, 'PENDENTE');
    assert.equal(res.cabecalhos.Location, '/jobs/job-13/status');
  } finally {
    restaurar();
  }
});

test('inserir publica a saga com os dados do formulario', async () => {
  const recebido = [];
  const restaurar = comSagaDublada(async (tipo, dominio, payload) => {
    recebido.push({ tipo, dominio, payload });
    return jobPendente('job-1', 'gerentes');
  });
  try {
    await inserir({ body: { ...NOVO_GERENTE, extra: 'ignorado' }, user: { cpf: CPF_LOGADO } }, resposta());

    // precisa bater com InserirGerenteSaga.TIPO e dominioJob() no ms-orquestrador
    assert.equal(recebido[0].tipo, 'inserir-gerente');
    assert.equal(recebido[0].dominio, 'gerentes');
    assert.deepEqual(recebido[0].payload, NOVO_GERENTE);
  } finally {
    restaurar();
  }
});

test('inserir sem senha devolve 400 sem publicar', async () => {
  let publicou = false;
  const restaurar = comSagaDublada(async () => {
    publicou = true;
    return jobPendente('job-1', 'gerentes');
  });
  try {
    const res = resposta();
    const { senha, ...semSenha } = NOVO_GERENTE;
    await inserir({ body: semSenha, user: { cpf: CPF_LOGADO } }, res);

    assert.equal(res.codigo, 400);
    assert.equal(res.corpo.status, 400);
    assert.equal(res.corpo.erro, 'Bad Request');
    assert.match(res.corpo.mensagem, /senha/);
    assert.equal(publicou, false);
  } finally {
    restaurar();
  }
});

test('inserir sem telefone ou com CPF fora do formato devolve 400 sem publicar', async () => {
  let publicou = false;
  const restaurar = comSagaDublada(async () => {
    publicou = true;
    return jobPendente('job-1', 'gerentes');
  });
  try {
    const semTelefone = resposta();
    await inserir({ body: { ...NOVO_GERENTE, telefone: ' ' }, user: { cpf: CPF_LOGADO } }, semTelefone);
    assert.equal(semTelefone.codigo, 400);
    assert.match(semTelefone.corpo.mensagem, /telefone/);

    const cpfComPontuacao = resposta();
    await inserir({ body: { ...NOVO_GERENTE, cpf: '111.444.777-35' }, user: { cpf: CPF_LOGADO } }, cpfComPontuacao);
    assert.equal(cpfComPontuacao.codigo, 400);
    assert.match(cpfComPontuacao.corpo.mensagem, /CPF/);

    assert.equal(publicou, false);
  } finally {
    restaurar();
  }
});

test('inserir com falha ao publicar vira 502', async () => {
  const restaurar = comSagaDublada(async () => {
    throw new Error('broker fora do ar');
  });
  try {
    const res = resposta();
    await inserir({ body: NOVO_GERENTE, user: { cpf: CPF_LOGADO } }, res);

    assert.equal(res.codigo, 502);
  } finally {
    restaurar();
  }
});

test('a constante de insercao bate com o tipo da saga', () => {
  assert.equal(TIPO_INSERIR_GERENTE, 'inserir-gerente');
});

test('listar responde 200 com a composição dentro de { gerentes }', async () => {
  const gerentesService = require('../services/gerentes.service');
  const original = gerentesService.listarTodos;
  const lista = [{ cpf: CPF_ALVO, nome: 'Godophredo', ativo: true, quantidadeClientes: 2 }];
  gerentesService.listarTodos = async () => lista;

  try {
    const res = resposta();
    await listar({}, res);
    assert.equal(res.codigo, null);
    assert.deepEqual(res.corpo, { gerentes: lista, _links: { self: { href: '/gerentes' } } });
  } finally {
    gerentesService.listarTodos = original;
  }
});

test('listar responde 502 quando um microsserviço falha', async () => {
  const gerentesService = require('../services/gerentes.service');
  const original = gerentesService.listarTodos;
  gerentesService.listarTodos = async () => {
    throw new Error('MS Gerente respondeu 500');
  };

  try {
    const res = resposta();
    await listar({}, res);
    assert.equal(res.codigo, 502);
  } finally {
    gerentesService.listarTodos = original;
  }
});

test('buscar devolve o gerente do MS Gerente', async () => {
  const gerenteService = require('../services/gerente.service');
  const original = gerenteService.buscar;
  const gerente = { cpf: CPF_ALVO, nome: 'Godophredo', email: 'ger2@bantads.com.br', ativo: true };
  gerenteService.buscar = async () => gerente;

  try {
    const res = resposta();
    await buscar({ params: { cpf: CPF_ALVO } }, res);
    assert.equal(res.codigo, null);
    assert.deepEqual(res.corpo, gerente);
  } finally {
    gerenteService.buscar = original;
  }
});

test('buscar gerente inexistente responde 404', async () => {
  const gerenteService = require('../services/gerente.service');
  const original = gerenteService.buscar;
  gerenteService.buscar = async () => {
    throw new gerenteService.GerenteNaoEncontradoError();
  };

  try {
    const res = resposta();
    await buscar({ params: { cpf: '00000000000' } }, res);
    assert.equal(res.codigo, 404);
    assert.deepEqual(res.corpo, { status: 404, erro: 'Not Found', mensagem: 'Gerente não encontrado.' });
  } finally {
    gerenteService.buscar = original;
  }
});

test('buscar com o MS fora do ar responde 502', async () => {
  const gerenteService = require('../services/gerente.service');
  const original = gerenteService.buscar;
  gerenteService.buscar = async () => {
    throw new Error('MS Gerente respondeu 500');
  };

  try {
    const res = resposta();
    await buscar({ params: { cpf: CPF_ALVO } }, res);
    assert.equal(res.codigo, 502);
  } finally {
    gerenteService.buscar = original;
  }
});
