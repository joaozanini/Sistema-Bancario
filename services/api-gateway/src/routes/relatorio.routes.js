const { Router } = require('express');
const { verifyJwtAndSession, exigirTipo } = require('../middlewares/auth.middleware');
const { gerar } = require('../controllers/relatorio.controller');

const router = Router();

// R16: o relatório expõe salário e saldo de todos os clientes — só gerente
router.get('/relatorios/clientes', verifyJwtAndSession, exigirTipo('GERENTE'), gerar);

module.exports = router;
