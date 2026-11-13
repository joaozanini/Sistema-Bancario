const { STATUS_CODES } = require('node:http');

// Corpo padrão de erro do contrato: { status, erro, mensagem }. "erro" é o nome
// do status HTTP (ex.: Unprocessable Entity) e "mensagem" é o texto para o usuário.
// O 401 fica de fora: ele segue o { auth, message } do verifyJWT (enunciado 5.4).
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

// Os MSs respondem erro como { erro: "texto" }; quando o texto não vem (corpo vazio
// ou o JSON padrão do Spring), vale uma mensagem genérica para o status.
function mensagemDoMs(corpo, status) {
  const texto = corpo && typeof corpo === 'object' ? corpo.mensagem || corpo.erro || corpo.message : null;
  if (typeof texto === 'string' && texto.trim()) {
    return texto;
  }
  return MENSAGENS_PADRAO[status] || (status >= 500 ? 'Serviço indisponível.' : 'Não foi possível concluir a operação.');
}

module.exports = { corpoErro, responderErro, mensagemDoMs };
