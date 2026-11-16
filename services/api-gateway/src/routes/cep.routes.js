const { Router } = require('express');
const { consultar } = require('../controllers/cep.controller');

const router = Router();

// consulta de CEP pública
router.get('/cep/:cep', consultar);

module.exports = router;
