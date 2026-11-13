// Consulta de endereço pelo CEP no serviço público ViaCEP. Quem chama é o Gateway
// porque o front só pode conversar com ele (enunciado, seções 3 e 5.6).
const URL_VIACEP = 'https://viacep.com.br/ws';
const TEMPO_LIMITE_MS = 5000;

class CepNaoEncontradoError extends Error {
  constructor() {
    super('CEP não encontrado.');
    this.name = 'CepNaoEncontradoError';
  }
}

// cep: os 8 dígitos, sem máscara
async function buscar(cep) {
  const resposta = await fetch(`${URL_VIACEP}/${cep}/json/`, { signal: AbortSignal.timeout(TEMPO_LIMITE_MS) });
  if (!resposta.ok) {
    throw new Error(`ViaCEP respondeu ${resposta.status}`);
  }

  // CEP bem formado que não existe volta 200 com { erro: "true" }
  const dados = await resposta.json();
  if (dados.erro) {
    throw new CepNaoEncontradoError();
  }

  return {
    cep,
    logradouro: dados.logradouro ?? '',
    complemento: dados.complemento ?? '',
    cidade: dados.localidade ?? '',
    uf: dados.uf ?? '',
  };
}

module.exports = { buscar, CepNaoEncontradoError, URL_VIACEP };
