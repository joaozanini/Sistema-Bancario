const { Router } = require('express');
const { verifyJwtAndSession, exigirTipo } = require('../middlewares/auth.middleware');
const { gerar } = require('../controllers/relatorio.controller');

const router = Router();

// R16: relatório de clientes, só para gerente
router.get('/relatorios/clientes', verifyJwtAndSession, exigirTipo('GERENTE'), gerar);

module.exports = router;
