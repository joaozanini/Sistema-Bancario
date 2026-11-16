const { Router } = require('express');
const { contaProxy } = require('../config/proxies');
const { listar, buscar } = require('../controllers/clientes.controller');
const { verifyJwtAndSession, injectUserHeaders, exigirTipo } = require('../middlewares/auth.middleware');

const router = Router();

// R11: API Composition (dados cadastrais do MS Cliente + saldo do MS Conta)
router.get('/clientes', verifyJwtAndSession, exigirTipo('GERENTE'), listar);

// R3: conta do cliente, repassada ao MS Conta
router.get('/clientes/:cpf/conta', verifyJwtAndSession, injectUserHeaders, contaProxy);

// R9: busca o cliente pelo CPF
router.get('/clientes/:cpf', verifyJwtAndSession, buscar);

module.exports = router;
