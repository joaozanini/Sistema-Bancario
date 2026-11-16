const { createProxyMiddleware, fixRequestBody } = require('http-proxy-middleware');
const { contaServiceUrl, clienteServiceUrl } = require('./services');
const { interceptarRespostaDoProxy } = require('../middlewares/links.middleware');
const { responderErro } = require('../services/erro.service');

// responde 502 quando o MS está fora do ar
function aoFalhar(err, req, res) {
  console.error(`Falha ao repassar ${req.method} ${req.originalUrl}:`, err.message);
  if (res.headersSent || typeof res.status !== 'function') {
    return res.end();
  }
  return responderErro(res, 502, 'Serviço indisponível.');
}

// cria o proxy REST para o MS, tratando links e erros na resposta
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
