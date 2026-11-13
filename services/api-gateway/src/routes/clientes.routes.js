const { Router } = require('express');
const { contaProxy } = require('../config/proxies');
const { listar, buscar } = require('../controllers/clientes.controller');
const { verifyJwtAndSession, injectUserHeaders, exigirTipo } = require('../middlewares/auth.middleware');

const router = Router();

// R11: API Composition (dados cadastrais do MS Cliente + saldo do MS Conta)
router.get('/clientes', verifyJwtAndSession, exigirTipo('GERENTE'), listar);

// R3: a conta é sub-recurso do cliente, mas quem a conhece é o MS Conta. Ele recebe a
// identidade nos headers e decide: o gerente vê qualquer conta, o cliente só a própria.
router.get('/clientes/:cpf/conta', verifyJwtAndSession, injectUserHeaders, contaProxy);

// R9: destino do job com resultType "resource" (GET /clientes/<resourceId>)
router.get('/clientes/:cpf', verifyJwtAndSession, buscar);

module.exports = router;
