const { Router } = require('express');
const { createProxyMiddleware, fixRequestBody } = require('http-proxy-middleware');
const { contaServiceUrl } = require('../config/services');
const { verifyJwtAndSession, injectUserHeaders } = require('../middlewares/auth.middleware');

const router = Router();
router.use(
  '/contas',
  verifyJwtAndSession,
  injectUserHeaders,
  // express.json() já consumiu o stream do corpo; sem isso todo POST/PUT proxiado trava
  createProxyMiddleware({
    target: contaServiceUrl,
    changeOrigin: true,
    on: { proxyReq: fixRequestBody },
  }),
);

module.exports = router;
