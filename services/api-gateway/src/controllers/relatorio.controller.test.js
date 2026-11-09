const test = require('node:test');
const assert = require('node:assert/strict');

const jobService = require('../services/job.service');
const relatorioService = require('../services/relatorio.service');
const { gerar, DOMINIO } = require('./relatorio.controller');

const LINHAS = [{ cpf: '12912861012', nome: 'Catharyna', saldo: '1500.0000' }];

function comDublos({ aoGerar, aoCriar } = {}) {
  const originais = {
    criar: jobService.criar,
    concluirInline: jobService.concluirInline,
    falhar: jobService.falhar,
    gerar: relatorioService.gerar,
  };
  const eventos = [];
  let terminou;
  const concluido = new Promise((resolve) => {
    terminou = resolve;
  });

  jobService.criar = async (jobId, dominio) => {
    eventos.push({ tipo: 'criar', jobId, dominio });
    if (aoCriar) aoCriar();
  };
  jobService.concluirInline = async (jobId, resultado) => {
    eventos.push({ tipo: 'concluir', jobId, resultado });
    terminou();
  };
  jobService.falhar = async (jobId, erro) => {
    eventos.push({ tipo: 'falhar', jobId, erro });
    terminou();
  };
  relatorioService.gerar = async () => {
    if (aoGerar) return aoGerar();
    return LINHAS;
  };

  return {
    eventos,
    concluido,
    restaurar() {
      Object.assign(jobService, {
        criar: originais.criar,
        concluirInline: originais.concluirInline,
        falhar: originais.falhar,
      });
      relatorioService.gerar = originais.gerar;
    },
  };
}

function respostaFalsa() {
  return {
    codigo: null,
    corpo: null,
    status(c) {
      this.codigo = c;
      return this;
    },
    json(b) {
      this.corpo = b;
      return this;
    },
  };
}

test('responde 202 com o jobId antes de compor', async () => {
  const d = comDublos();
  try {
    const res = respostaFalsa();
    await gerar({}, res);

    assert.equal(res.codigo, 202);
    assert.match(res.corpo.jobId, /^[0-9a-f-]{36}$/);

    const criou = d.eventos.find((e) => e.tipo === 'criar');
    assert.equal(criou.jobId, res.corpo.jobId, 'o job criado tem de ser o jobId devolvido');
    assert.equal(criou.dominio, DOMINIO);
    assert.equal(
      d.eventos.indexOf(criou),
      0,
      'o job nasce antes de qualquer coisa: senão a conclusão não acha o que atualizar',
    );
  } finally {
    d.restaurar();
  }
});

test('conclui o job inline com as linhas do relatorio', async () => {
  const d = comDublos();
  try {
    const res = respostaFalsa();
    await gerar({}, res);
    await d.concluido;

    const fim = d.eventos.find((e) => e.tipo === 'concluir');
    assert.equal(fim.jobId, res.corpo.jobId);
    assert.deepEqual(fim.resultado, LINHAS);
    assert.equal(d.eventos.some((e) => e.tipo === 'falhar'), false);
  } finally {
    d.restaurar();
  }
});

test('falha na composicao marca o job como FALHA e nao derruba o processo', async () => {
  const d = comDublos({
    aoGerar() {
      throw new Error('MS Conta respondeu 500');
    },
  });

  try {
    const res = respostaFalsa();
    await gerar({}, res);
    await d.concluido;

    assert.equal(res.codigo, 202, 'o 202 já foi enviado antes da falha');
    const falhou = d.eventos.find((e) => e.tipo === 'falhar');
    assert.equal(falhou.jobId, res.corpo.jobId);
    assert.match(falhou.erro, /relat[oó]rio/i);
    assert.equal(d.eventos.some((e) => e.tipo === 'concluir'), false);
  } finally {
    d.restaurar();
  }
});

test('redis fora do ar na criacao do job responde 503 e nao compoe', async () => {
  const d = comDublos({
    aoCriar() {
      throw new Error('conexao recusada');
    },
  });

  try {
    const res = respostaFalsa();
    await gerar({}, res);

    assert.equal(res.codigo, 503);
    assert.equal(
      d.eventos.some((e) => e.tipo === 'concluir' || e.tipo === 'falhar'),
      false,
      'sem job no Redis não há onde publicar o resultado',
    );
  } finally {
    d.restaurar();
  }
});
