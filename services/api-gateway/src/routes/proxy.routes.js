const { Router } = require('express');
const { createProxyMiddleware, fixRequestBody } = require('http-proxy-middleware');
const { contaServiceUrl, gerenteServiceUrl } = require('../config/services');
const { verifyJwtAndSession, injectUserHeaders, exigirTipo } = require('../middlewares/auth.middleware');
const { enriquecerCpfDestino, enriquecerNomes } = require('../middlewares/transferencia.middleware');
const { inserir, remover } = require('../controllers/gerentes.controller');

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

// R13 é SAGA: publica em saga.cmd e responde 202
router.post('/gerentes', verifyJwtAndSession, exigirTipo('GERENTE'), inserir);

router.put('/gerentes/:cpf', verifyJwtAndSession, exigirTipo('GERENTE'), injectUserHeaders, gerenteProxy);

// R15 é SAGA: não vai para o MS Gerente via REST, vira mensagem em saga.cmd
router.delete('/gerentes/:cpf', verifyJwtAndSession, exigirTipo('GERENTE'), remover);

module.exports = router;
