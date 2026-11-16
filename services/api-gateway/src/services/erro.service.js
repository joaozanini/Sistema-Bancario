const { STATUS_CODES } = require('node:http');

// monta o corpo de erro { status, erro, mensagem }
function corpoErro(status, mensagem) {
  return { status, erro: STATUS_CODES[status] || 'Erro', mensagem };
}

function responderErro(res, status, mensagem) {
  return res.status(status).json(corpoErro(status, mensagem));
}

const MENSAGENS_PADRAO = {
  400: 'Requisição inválida.',
  403: 'Acesso negado.',
  404: 'Recurso não encontrado.',
  409: 'Conflito com o estado atual do recurso.',
  422: 'Regra de negócio violada.',
};

// extrai a mensagem de erro do MS ou usa uma genérica pelo status
function mensagemDoMs(corpo, status) {
  const texto = corpo && typeof corpo === 'object' ? corpo.mensagem || corpo.erro || corpo.message : null;
  if (typeof texto === 'string' && texto.trim()) {
    return texto;
  }
  return MENSAGENS_PADRAO[status] || (status >= 500 ? 'Serviço indisponível.' : 'Não foi possível concluir a operação.');
}

module.exports = { corpoErro, responderErro, mensagemDoMs };
