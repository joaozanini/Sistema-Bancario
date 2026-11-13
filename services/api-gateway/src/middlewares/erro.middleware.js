const { responderErro } = require('../services/erro.service');

// Caminho que não é de nenhuma rota do contrato: sem isto o Express devolve uma página HTML
function rotaNaoEncontrada(req, res) {
  return responderErro(res, 404, 'Rota não encontrada.');
}

// Último middleware: o que escapar das rotas ainda sai no formato de erro do contrato.
// O caso comum é o corpo com JSON malformado, recusado pelo express.json().
// O parâmetro next não é usado, mas precisa existir: o Express só reconhece o
// tratador de erro pela assinatura de quatro parâmetros.
function tratarErro(err, req, res, next) {
  if (err.type === 'entity.parse.failed') {
    return responderErro(res, 400, 'O corpo da requisição não é um JSON válido.');
  }
  console.error(`Erro não tratado em ${req.method} ${req.originalUrl}:`, err.message);
  return responderErro(res, err.status >= 400 && err.status < 500 ? err.status : 500, 'Não foi possível concluir a operação.');
}

module.exports = { rotaNaoEncontrada, tratarErro };
