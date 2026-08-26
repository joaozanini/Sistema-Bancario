const { Router } = require('express');
const { reboot } = require('../controllers/reboot.controller');

const router = Router();
router.post('/reboot', reboot);

module.exports = router;
