const { Router } = require('express');
const { createProxyMiddleware, fixRequestBody } = require('http-proxy-middleware');
const { contaServiceUrl } = require('../config/services');
const { verifyJwtAndSession, injectUserHeaders } = require('../middlewares/auth.middleware');
const { enriquecerCpfDestino, enriquecerNomes } = require('../middlewares/transferencia.middleware');

const router = Router();

const contaProxy = createProxyMiddleware({
  target: contaServiceUrl,
  changeOrigin: true,
  on: { proxyReq: fixRequestBody },
});

router.post(
  '/contas/:numero/transferencia',
  verifyJwtAndSession,
  injectUserHeaders,
  enriquecerCpfDestino,
  enriquecerNomes,
  contaProxy,
);

// router.all (e não router.use) para o Express não tirar o prefixo /contas do req.url repassado ao MS.
router.all(['/contas', '/contas/*'], verifyJwtAndSession, injectUserHeaders, contaProxy);

module.exports = router;
