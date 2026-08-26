const { Router } = require('express');
const { createProxyMiddleware } = require('http-proxy-middleware');
const { contaServiceUrl } = require('../config/services');

const router = Router();
router.use('/contas', createProxyMiddleware({ target: contaServiceUrl, changeOrigin: true }));

module.exports = router;
