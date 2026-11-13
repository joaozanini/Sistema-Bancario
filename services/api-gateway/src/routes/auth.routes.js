const { Router } = require("express");
const { login, logout } = require("../controllers/auth.controller");
const { verifyJwtAndSession } = require("../middlewares/auth.middleware");

const router = Router();

router.post("/login", login);
router.post("/logout", verifyJwtAndSession, logout);

module.exports = router;
