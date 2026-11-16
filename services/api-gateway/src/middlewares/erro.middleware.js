const { responderErro } = require('../services/erro.service');

// responde 404 no formato de erro do contrato
function rotaNaoEncontrada(req, res) {
  return responderErro(res, 404, 'Rota não encontrada.');
}

// trata erros não capturados no formato do contrato (JSON malformado vira 400)
function tratarErro(err, req, res, next) {
  if (err.type === 'entity.parse.failed') {
    return responderErro(res, 400, 'O corpo da requisição não é um JSON válido.');
  }
  console.error(`Erro não tratado em ${req.method} ${req.originalUrl}:`, err.message);
  return responderErro(res, err.status >= 400 && err.status < 500 ? err.status : 500, 'Não foi possível concluir a operação.');
}

module.exports = { rotaNaoEncontrada, tratarErro };
