const test = require('node:test');
const assert = require('node:assert/strict');

const { buscarNome, ClienteNaoEncontradoError } = require('./cliente.service');

function comFetch(resposta, aoChamar) {
  const original = globalThis.fetch;
  globalThis.fetch = async (url) => {
    if (aoChamar) aoChamar(url);
    return resposta;
  };
  return () => {
    globalThis.fetch = original;
  };
}

test('devolve o nome do cliente', async () => {
  let urlChamada;
  const restaurar = comFetch(
    { ok: true, status: 200, json: async () => ({ cpf: '12912861012', nome: 'Catharyna' }) },
    (url) => {
      urlChamada = url;
    },
  );

  try {
    assert.equal(await buscarNome('12912861012'), 'Catharyna');
    assert.match(urlChamada, /\/clientes\/12912861012$/);
  } finally {
    restaurar();
  }
});

test('404 do MS Cliente vira ClienteNaoEncontradoError', async () => {
  const restaurar = comFetch({ ok: false, status: 404, json: async () => ({}) });

  try {
    await assert.rejects(() => buscarNome('00000000000'), ClienteNaoEncontradoError);
  } finally {
    restaurar();
  }
});

test('erro do MS Cliente nao vira 404 silencioso', async () => {
  const restaurar = comFetch({ ok: false, status: 500, json: async () => ({}) });

  try {
    await assert.rejects(() => buscarNome('12912861012'), (err) => {
      assert.ok(!(err instanceof ClienteNaoEncontradoError));
      assert.match(err.message, /500/);
      return true;
    });
  } finally {
    restaurar();
  }
});

test('resposta 200 sem nome falha alto em vez de devolver undefined', async () => {
  const restaurar = comFetch({ ok: true, status: 200, json: async () => ({ cpf: '12912861012' }) });

  try {
    await assert.rejects(() => buscarNome('12912861012'), /nao retornou o nome|não retornou o nome/);
  } finally {
    restaurar();
  }
});

test('cpf é escapado na URL', async () => {
  let urlChamada;
  const restaurar = comFetch(
    { ok: true, status: 200, json: async () => ({ nome: 'X' }) },
    (url) => {
      urlChamada = url;
    },
  );

  try {
    await buscarNome('../gerentes/999');
    assert.ok(!urlChamada.includes('../'), `URL nao deveria conter path traversal: ${urlChamada}`);
  } finally {
    restaurar();
  }
});
