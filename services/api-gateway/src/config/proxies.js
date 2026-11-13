const { createProxyMiddleware, fixRequestBody } = require('http-proxy-middleware');
const { contaServiceUrl, clienteServiceUrl } = require('./services');
const { interceptarRespostaDoProxy } = require('../middlewares/links.middleware');
const { responderErro } = require('../services/erro.service');

// MS fora do ar: sem isto o http-proxy-middleware responde um 504 em texto puro
function aoFalhar(err, req, res) {
  console.error(`Falha ao repassar ${req.method} ${req.originalUrl}:`, err.message);
  if (res.headersSent || typeof res.status !== 'function') {
    return res.end();
  }
  return responderErro(res, 502, 'Serviço indisponível.');
}

// Roteamento simples (enunciado 5.3): o Gateway repassa a requisição ao MS por REST
// e trata a resposta na volta (links do HATEOAS e formato de erro).
function criarProxy(target, aoResponder = interceptarRespostaDoProxy) {
  return createProxyMiddleware({
    target,
    changeOrigin: true,
    selfHandleResponse: true,
    on: { proxyReq: fixRequestBody, proxyRes: aoResponder, error: aoFalhar },
  });
}

const contaProxy = criarProxy(contaServiceUrl);
const clienteProxy = criarProxy(clienteServiceUrl);

module.exports = { criarProxy, contaProxy, clienteProxy };
