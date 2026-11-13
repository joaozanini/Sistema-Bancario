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

// R14: invalida só depois que o MS Gerente confirmou a gravação — invalidar
// antes abriria janela para uma leitura concorrente recachear o dado velho.
const gerenteProxy = criarProxy(gerenteServiceUrl, (proxyRes, req, res) => {
  if (req.method === 'PUT' && proxyRes.statusCode < 400 && req.params?.cpf) {
    gerenteService.invalidar(req.params.cpf);
  }
  return interceptarRespostaDoProxy(proxyRes, req, res);
});

// Só as rotas do contrato chegam ao MS Conta. Cada uma é declarada por inteiro
// (em vez de um router.use('/contas')) para o Express não tirar o prefixo /contas
// do req.url repassado ao MS.

// R3: gerente consulta qualquer conta; o cliente, só a própria (quem confere é o MS Conta)
router.get('/contas/:numero', verifyJwtAndSession, injectUserHeaders, contaProxy);

// R4 e R5: só cliente movimenta; a posse da conta é conferida no MS Conta pelo X-User-CPF
router.post('/contas/:numero/deposito', verifyJwtAndSession, exigirTipo('CLIENTE'), injectUserHeaders, contaProxy);
router.post('/contas/:numero/saque', verifyJwtAndSession, exigirTipo('CLIENTE'), injectUserHeaders, contaProxy);

// R6: o Gateway enriquece a requisição (CPF e nomes) antes de rotear ao MS Conta
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

// R7: o titular consulta o próprio extrato e o gerente, o de qualquer conta
router.get('/contas/:numero/extrato', verifyJwtAndSession, injectUserHeaders, contaProxy);

// R12: API Composition (gerentes ativos + quantidade de clientes de cada um)
router.get('/gerentes', verifyJwtAndSession, exigirTipo('GERENTE'), listar);

// R13 é SAGA: publica em saga.cmd e responde 202
router.post('/gerentes', verifyJwtAndSession, exigirTipo('GERENTE'), inserir);

// R14: sem esta rota a tela de edicao recebe 404 e nao carrega o gerente
router.get('/gerentes/:cpf', verifyJwtAndSession, exigirTipo('GERENTE'), buscarGerente);

router.put('/gerentes/:cpf', verifyJwtAndSession, exigirTipo('GERENTE'), injectUserHeaders, gerenteProxy);

// R15 é SAGA: não vai para o MS Gerente via REST, vira mensagem em saga.cmd
router.delete('/gerentes/:cpf', verifyJwtAndSession, exigirTipo('GERENTE'), remover);

module.exports = router;
