const { Router } = require("express");
const { login, logout } = require("../controllers/auth.controller");
const { verifyJwtAndSession } = require("../middlewares/auth.middleware");

const router = Router();

router.post(["/login", "/auth/login"], login);
router.post(["/logout", "/auth/logout"], verifyJwtAndSession, logout);

module.exports = router;
