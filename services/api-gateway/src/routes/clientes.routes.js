const { Router } = require('express');
const { listar } = require('../controllers/clientes.controller');
const { verifyJwtAndSession, exigirTipo } = require('../middlewares/auth.middleware');

const router = Router();
router.get('/clientes', verifyJwtAndSession, exigirTipo('GERENTE'), listar);

module.exports = router;
