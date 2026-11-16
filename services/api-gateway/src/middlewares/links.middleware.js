const { responseInterceptor } = require('http-proxy-middleware');
const { baseUrl, reescreverLinks } = require('../services/links.service');
const { corpoErro, mensagemDoMs } = require('../services/erro.service');

// reescreve os _links das respostas montadas pelo Gateway
function reescreverLinksDeRespostaJson(req, res, next) {
  const json = res.json.bind(res);
  res.json = (corpo) => json(reescreverLinks(corpo, baseUrl(req)));
  next();
}

function lerJson(buffer) {
  try {
    return JSON.parse(buffer.toString('utf8'));
  } catch {
    return null;
  }
}

// reescreve os _links das respostas do proxy e padroniza os erros
const interceptarRespostaDoProxy = responseInterceptor(async (buffer, proxyRes, req, res) => {
  const ehJson = String(proxyRes.headers['content-type'] || '').includes('application/json');
  const corpo = ehJson ? lerJson(buffer) : null;

  if (proxyRes.statusCode >= 400) {
    res.setHeader('content-type', 'application/json; charset=utf-8');
    return JSON.stringify(corpoErro(proxyRes.statusCode, mensagemDoMs(corpo, proxyRes.statusCode)));
  }

  // qualquer outro conteúdo (ou JSON inválido) segue intacto
  return corpo === null ? buffer : JSON.stringify(reescreverLinks(corpo, baseUrl(req)));
});

module.exports = { reescreverLinksDeRespostaJson, interceptarRespostaDoProxy };
