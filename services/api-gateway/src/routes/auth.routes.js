const { Router } = require('express');
const { login, logout } = require('../controllers/auth.controller');
const { verifyJwtAndSession } = require('../middlewares/auth.middleware');

const router = Router();
// /login é a rota que a suíte de testes usa; /auth/login é a que o front já chama
router.post(['/login', '/auth/login'], login);
router.post('/auth/logout', verifyJwtAndSession, logout);

module.exports = router;
