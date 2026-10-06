const { Router } = require('express');
const { status, resultado } = require('../controllers/jobs.controller');
const { verifyJwtAndSession } = require('../middlewares/auth.middleware');

const router = Router();
router.get('/jobs/:id/status', verifyJwtAndSession, status);
router.get('/jobs/:id/result', verifyJwtAndSession, resultado);

module.exports = router;
