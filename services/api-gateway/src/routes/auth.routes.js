const { Router } = require('express');
const { login, logout } = require('../controllers/auth.controller');
const { verifyJwtAndSession } = require('../middlewares/auth.middleware');

const router = Router();
router.post('/auth/login', login);
router.post('/auth/logout', verifyJwtAndSession, logout);

module.exports = router;
