const { Router } = require('express');
const { createProxyMiddleware, fixRequestBody } = require('http-proxy-middleware');
const { clienteServiceUrl } = require('../config/services');
const { verifyJwtAndSession, injectUserHeaders, exigirTipo } = require('../middlewares/auth.middleware');
const { aprovar } = require('../controllers/solicitacoes.controller');

const router = Router();

const clienteProxy = createProxyMiddleware({
  target: clienteServiceUrl,
  changeOrigin: true,
  on: { proxyReq: fixRequestBody },
});

// R1: autocadastro é público — quem se cadastra ainda não tem login
router.post('/solicitacoes', clienteProxy);

// R8: a tela inicial do gerente lista as solicitações em todos os status
router.get('/solicitacoes', verifyJwtAndSession, exigirTipo('GERENTE'), injectUserHeaders, clienteProxy);

router.post('/solicitacoes/:cpf/aprovar', verifyJwtAndSession, exigirTipo('GERENTE'), aprovar);

module.exports = router;
