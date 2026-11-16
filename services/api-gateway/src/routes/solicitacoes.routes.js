const { Router } = require('express');
const { clienteProxy } = require('../config/proxies');
const { verifyJwtAndSession, injectUserHeaders, exigirTipo } = require('../middlewares/auth.middleware');
const { aprovar } = require('../controllers/solicitacoes.controller');

const router = Router();

// R1: autocadastro público
router.post('/solicitacoes', clienteProxy);

// R8: lista as solicitações
router.get('/solicitacoes', verifyJwtAndSession, exigirTipo('GERENTE'), injectUserHeaders, clienteProxy);

router.get('/solicitacoes/:cpf', verifyJwtAndSession, exigirTipo('GERENTE'), injectUserHeaders, clienteProxy);

// R9: aprovação via SAGA (202)
router.post('/solicitacoes/:cpf/aprovacao', verifyJwtAndSession, exigirTipo('GERENTE'), aprovar);

// R10: rejeição síncrona, repassada ao MS Cliente
router.post('/solicitacoes/:cpf/rejeicao', verifyJwtAndSession, exigirTipo('GERENTE'), injectUserHeaders, clienteProxy);

module.exports = router;
