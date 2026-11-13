const test = require('node:test');
const assert = require('node:assert/strict');

const { consultar } = require('./cep.controller');
const { URL_VIACEP } = require('../services/cep.service');

function respostaFalsa() {
  return {
    codigo: 200,
    corpo: undefined,
    status(codigo) {
      this.codigo = codigo;
      return this;
    },
    json(corpo) {
      this.corpo = corpo;
      return this;
    },
  };
}

function comFetch(responder) {
  const original = globalThis.fetch;
  const chamadas = [];
  globalThis.fetch = async (url) => {
    chamadas.push(String(url));
    return responder();
  };
  return { chamadas, restaurar: () => (globalThis.fetch = original) };
}

test('devolve o endereço do CEP com os campos do autocadastro e o link self', async () => {
  const fetchFalso = comFetch(() => ({
    ok: true,
    status: 200,
    json: async () => ({ logradouro: 'Rua XV de Novembro', complemento: 'até 1299/1300', localidade: 'Curitiba', uf: 'PR' }),
  }));
  const res = respostaFalsa();

  try {
    await consultar({ params: { cep: '80060-000' } }, res);
  } finally {
    fetchFalso.restaurar();
  }

  assert.deepEqual(fetchFalso.chamadas, [`${URL_VIACEP}/80060000/json/`]);
  assert.equal(res.codigo, 200);
  assert.deepEqual(res.corpo, {
    cep: '80060000',
    logradouro: 'Rua XV de Novembro',
    complemento: 'até 1299/1300',
    cidade: 'Curitiba',
    uf: 'PR',
    _links: { self: { href: '/cep/80060000' } },
  });
});

test('CEP sem 8 dígitos responde 400 sem consultar o ViaCEP', async () => {
  const fetchFalso = comFetch(() => {
    throw new Error('não era para consultar');
  });
  const res = respostaFalsa();

  try {
    await consultar({ params: { cep: '8006' } }, res);
  } finally {
    fetchFalso.restaurar();
  }

  assert.deepEqual(fetchFalso.chamadas, []);
  assert.deepEqual(res.corpo, { status: 400, erro: 'Bad Request', mensagem: 'CEP deve ter 8 dígitos.' });
});

test('CEP que o ViaCEP não conhece responde 404', async () => {
  const fetchFalso = comFetch(() => ({ ok: true, status: 200, json: async () => ({ erro: 'true' }) }));
  const res = respostaFalsa();

  try {
    await consultar({ params: { cep: '99999999' } }, res);
  } finally {
    fetchFalso.restaurar();
  }

  assert.deepEqual(res.corpo, { status: 404, erro: 'Not Found', mensagem: 'CEP não encontrado.' });
});

test('ViaCEP fora do ar responde 502 e não derruba a requisição', async () => {
  const fetchFalso = comFetch(() => {
    throw new Error('fetch failed');
  });
  const res = respostaFalsa();

  try {
    await consultar({ params: { cep: '80060000' } }, res);
  } finally {
    fetchFalso.restaurar();
  }

  assert.deepEqual(res.corpo, { status: 502, erro: 'Bad Gateway', mensagem: 'Não foi possível consultar o CEP.' });
});

test('resposta de erro do ViaCEP também vira 502', async () => {
  const fetchFalso = comFetch(() => ({ ok: false, status: 500, json: async () => ({}) }));
  const res = respostaFalsa();

  try {
    await consultar({ params: { cep: '80060000' } }, res);
  } finally {
    fetchFalso.restaurar();
  }

  assert.equal(res.codigo, 502);
});
