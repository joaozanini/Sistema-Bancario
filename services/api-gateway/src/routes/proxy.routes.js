const { Router } = require('express');
const { gerenteServiceUrl } = require('../config/services');
const { criarProxy, contaProxy } = require('../config/proxies');
const { verifyJwtAndSession, injectUserHeaders, exigirTipo } = require('../middlewares/auth.middleware');
const {
  validarContaOrigem,
  enriquecerCpfDestino,
  enriquecerNomes,
} = require('../middlewares/transferencia.middleware');
const { listar, inserir, remover, buscar: buscarGerente } = require('../controllers/gerentes.controller');
const gerenteService = require('../services/gerente.service');
const { interceptarRespostaDoProxy } = require('../middlewares/links.middleware');

const router = Router();

// R14: invalida o cache do gerente após PUT bem-sucedido
const gerenteProxy = criarProxy(gerenteServiceUrl, (proxyRes, req, res) => {
  if (req.method === 'PUT' && proxyRes.statusCode < 400 && req.params?.cpf) {
    gerenteService.invalidar(req.params.cpf);
  }
  return interceptarRespostaDoProxy(proxyRes, req, res);
});

// R3: consulta a conta pelo número
router.get('/contas/:numero', verifyJwtAndSession, injectUserHeaders, contaProxy);

// R4 e R5: depósito e saque, só para cliente
router.post('/contas/:numero/deposito', verifyJwtAndSession, exigirTipo('CLIENTE'), injectUserHeaders, contaProxy);
router.post('/contas/:numero/saque', verifyJwtAndSession, exigirTipo('CLIENTE'), injectUserHeaders, contaProxy);

// R6: valida a origem, adiciona CPF e nomes e repassa ao MS Conta
router.post(
  '/contas/:numero/transferencia',
  verifyJwtAndSession,
  exigirTipo('CLIENTE'),
  injectUserHeaders,
  validarContaOrigem,
  enriquecerCpfDestino,
  enriquecerNomes,
  contaProxy,
);

// R7: extrato da conta
router.get('/contas/:numero/extrato', verifyJwtAndSession, injectUserHeaders, contaProxy);

// R12: lista gerentes com a quantidade de clientes (API Composition)
router.get('/gerentes', verifyJwtAndSession, exigirTipo('GERENTE'), listar);

// R13: inserção de gerente via SAGA (202)
router.post('/gerentes', verifyJwtAndSession, exigirTipo('GERENTE'), inserir);

// R14: busca o gerente pelo CPF
router.get('/gerentes/:cpf', verifyJwtAndSession, exigirTipo('GERENTE'), buscarGerente);

router.put('/gerentes/:cpf', verifyJwtAndSession, exigirTipo('GERENTE'), injectUserHeaders, gerenteProxy);

// R15: remoção de gerente via SAGA (202)
router.delete('/gerentes/:cpf', verifyJwtAndSession, exigirTipo('GERENTE'), remover);

module.exports = router;
