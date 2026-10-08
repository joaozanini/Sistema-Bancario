const { Router } = require('express');
const { createProxyMiddleware, fixRequestBody } = require('http-proxy-middleware');
const { contaServiceUrl, gerenteServiceUrl } = require('../config/services');
const { verifyJwtAndSession, injectUserHeaders, exigirTipo } = require('../middlewares/auth.middleware');
const { enriquecerCpfDestino, enriquecerNomes } = require('../middlewares/transferencia.middleware');

const router = Router();

const contaProxy = createProxyMiddleware({
  target: contaServiceUrl,
  changeOrigin: true,
  on: { proxyReq: fixRequestBody },
});

const gerenteProxy = createProxyMiddleware({
  target: gerenteServiceUrl,
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

// a listagem traz o saldo de todas as contas (R11, R12, R16): só gerente
router.get('/contas', verifyJwtAndSession, exigirTipo('GERENTE'), injectUserHeaders, contaProxy);

// router.all (e não router.use) para o Express não tirar o prefixo /contas do req.url repassado ao MS.
router.all(['/contas', '/contas/*'], verifyJwtAndSession, injectUserHeaders, contaProxy);

router.put('/gerentes/:cpf', verifyJwtAndSession, exigirTipo('GERENTE'), injectUserHeaders, gerenteProxy);

module.exports = router;
