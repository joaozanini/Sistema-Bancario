const { Router } = require('express');
const { consultar } = require('../controllers/cep.controller');

const router = Router();

// público, como o autocadastro (R1): quem preenche o endereço ainda não tem login
router.get('/cep/:cep', consultar);

module.exports = router;
