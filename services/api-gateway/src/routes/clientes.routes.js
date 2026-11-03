const { Router } = require('express');
const { listar, buscar } = require('../controllers/clientes.controller');
const { verifyJwtAndSession, exigirTipo } = require('../middlewares/auth.middleware');

const router = Router();
router.get('/clientes', verifyJwtAndSession, exigirTipo('GERENTE'), listar);

// R9: destino do job com resultType "resource" (GET /clientes/<resourceId>)
router.get('/clientes/:cpf', verifyJwtAndSession, buscar);

module.exports = router;
