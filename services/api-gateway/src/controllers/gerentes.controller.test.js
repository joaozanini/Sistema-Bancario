const test = require('node:test');
const assert = require('node:assert/strict');

const sagaService = require('../services/saga.service');
const redis = require('../config/redis');
const { listar, inserir, remover, TIPO_INSERIR_GERENTE, TIPO_REMOVER_GERENTE, DOMINIO } = require('./gerentes.controller');

const CPF_LOGADO = '98574307084';
const CPF_ALVO = '64065268052';

function resposta() {
  return {
    codigo: null,
    corpo: null,
    status(c) {
      this.codigo = c;
      return this;
    },
    json(c) {
      this.corpo = c;
      return this;
    },
  };
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
    return 'job-1';
  });

  try {
    const res = resposta();
    await remover({ params: { cpf: CPF_LOGADO }, user: { cpf: CPF_LOGADO } }, res);

    assert.equal(res.codigo, 403);
    assert.equal(publicou, false, 'a SAGA nao pode nem comecar');
  } finally {
    restaurar();
  }
});

test('remover outro gerente devolve 202 com o jobId', async () => {
  const restaurar = comSagaDublada(async () => 'job-123');
  try {
    const res = resposta();
    await remover({ params: { cpf: CPF_ALVO }, user: { cpf: CPF_LOGADO } }, res);

    assert.equal(res.codigo, 202);
    assert.deepEqual(res.corpo, { jobId: 'job-123' });
  } finally {
    restaurar();
  }
});

test('publica o tipo e o dominio da saga de remocao', async () => {
  const recebido = [];
  const restaurar = comSagaDublada(async (tipo, dominio, payload) => {
    recebido.push({ tipo, dominio, payload });
    return 'job-1';
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

test('inserir devolve 202 com o jobId', async () => {
  const restaurar = comSagaDublada(async () => 'job-13');
  try {
    const res = resposta();
    await inserir({ body: NOVO_GERENTE, user: { cpf: CPF_LOGADO } }, res);

    assert.equal(res.codigo, 202);
    assert.deepEqual(res.corpo, { jobId: 'job-13' });
  } finally {
    restaurar();
  }
});

test('inserir publica a saga com os dados do formulario', async () => {
  const recebido = [];
  const restaurar = comSagaDublada(async (tipo, dominio, payload) => {
    recebido.push({ tipo, dominio, payload });
    return 'job-1';
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
    return 'job-1';
  });
  try {
    const res = resposta();
    const { senha, ...semSenha } = NOVO_GERENTE;
    await inserir({ body: semSenha, user: { cpf: CPF_LOGADO } }, res);

    assert.equal(res.codigo, 400);
    assert.match(res.corpo.message, /senha/);
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

test('listar responde 200 com a composição de gerentes', async () => {
  const gerentesService = require('../services/gerentes.service');
  const original = gerentesService.listarTodos;
  const lista = [{ cpf: CPF_ALVO, nome: 'Godophredo', quantidadeClientes: 2 }];
  gerentesService.listarTodos = async () => lista;

  try {
    const res = resposta();
    await listar({}, res);
    assert.equal(res.codigo, null);
    assert.deepEqual(res.corpo, lista);
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
