const { responseInterceptor } = require('http-proxy-middleware');
const { baseUrl, reescreverLinks } = require('../services/links.service');

// Respostas montadas pelo próprio Gateway (API Composition, login etc.) passam por res.json.
// Os endpoints de login, jobs e /health não carregam _links, então nada muda neles.
function reescreverLinksDeRespostaJson(req, res, next) {
  const json = res.json.bind(res);
  res.json = (corpo) => json(reescreverLinks(corpo, baseUrl(req)));
  next();
}

// Respostas repassadas pelo proxy: o corpo vem do MS com os links internos.
// Só mexe em JSON; qualquer outro conteúdo (ou JSON inválido) segue intacto.
const interceptarRespostaDoProxy = responseInterceptor(async (buffer, proxyRes, req) => {
  if (!String(proxyRes.headers['content-type'] || '').includes('application/json')) {
    return buffer;
  }
  try {
    const corpo = JSON.parse(buffer.toString('utf8'));
    return JSON.stringify(reescreverLinks(corpo, baseUrl(req)));
  } catch {
    return buffer;
  }
});

module.exports = { reescreverLinksDeRespostaJson, interceptarRespostaDoProxy };
