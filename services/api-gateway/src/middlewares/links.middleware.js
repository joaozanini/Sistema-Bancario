const { responseInterceptor } = require('http-proxy-middleware');
const { baseUrl, reescreverLinks } = require('../services/links.service');
const { corpoErro, mensagemDoMs } = require('../services/erro.service');

// Respostas montadas pelo próprio Gateway (API Composition, login etc.) passam por res.json.
// Os endpoints de login, jobs e /health não carregam _links, então nada muda neles.
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

// Respostas repassadas pelo proxy. Sucesso: o corpo vem do MS com os links internos
// e sai com os links do Gateway. Erro: cada MS tem o seu formato ({ erro }, corpo
// vazio, JSON do Spring) e todos saem no formato do contrato, { status, erro, mensagem }.
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
