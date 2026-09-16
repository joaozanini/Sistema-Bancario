const { Router } = require('express');
const { createProxyMiddleware, fixRequestBody } = require('http-proxy-middleware');
const { contaServiceUrl } = require('../config/services');
const { verifyJwtAndSession, injectUserHeaders } = require('../middlewares/auth.middleware');
const { enriquecerCpfDestino } = require('../middlewares/transferencia.middleware');

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
  contaProxy,
);

router.use('/contas', verifyJwtAndSession, injectUserHeaders, contaProxy);

module.exports = router;
