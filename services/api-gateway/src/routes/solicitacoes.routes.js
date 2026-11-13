const { Router } = require('express');
const { clienteProxy } = require('../config/proxies');
const { verifyJwtAndSession, injectUserHeaders, exigirTipo } = require('../middlewares/auth.middleware');
const { aprovar } = require('../controllers/solicitacoes.controller');

const router = Router();

// R1: autocadastro é público — quem se cadastra ainda não tem login
router.post('/solicitacoes', clienteProxy);

// R8: a tela inicial do gerente lista as solicitações em todos os status
router.get('/solicitacoes', verifyJwtAndSession, exigirTipo('GERENTE'), injectUserHeaders, clienteProxy);

router.get('/solicitacoes/:cpf', verifyJwtAndSession, exigirTipo('GERENTE'), injectUserHeaders, clienteProxy);

// R9 é SAGA: publica em saga.cmd e responde 202
router.post('/solicitacoes/:cpf/aprovacao', verifyJwtAndSession, exigirTipo('GERENTE'), aprovar);

// R10: rejeição é síncrona (200), não SAGA — o MS Cliente grava e publica o e-mail
router.post('/solicitacoes/:cpf/rejeicao', verifyJwtAndSession, exigirTipo('GERENTE'), injectUserHeaders, clienteProxy);

module.exports = router;
