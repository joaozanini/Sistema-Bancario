const authService = require("../services/auth.service");
const { responderErro } = require("../services/erro.service");

async function login(req, res) {
  const { senha } = req.body || {};
  const email = req.body?.email ?? req.body?.login;
  if (!email || !senha) {
    return responderErro(res, 400, "E-mail e senha são obrigatórios.");
  }

  try {
    const { token, tipo, usuario } = await authService.autenticar(email, senha);
    return res.json({ auth: true, token, tipo, usuario });
  } catch (err) {
    if (err instanceof authService.CredenciaisInvalidasError) {
      return res.status(401).json({ auth: false, message: "Login inválido!" });
    }
    console.error("Falha no login (API Composition):", err.message);
    return responderErro(res, 502, "Serviço de autenticação indisponível.");
  }
}

async function logout(req, res) {
  const { jti, exp } = req.tokenMeta;
  const { cpf } = req.user;

  try {
    await authService.encerrarSessao(jti, cpf, exp);
    // 204: a sessão foi encerrada e não há nada a devolver
    return res.status(204).end();
  } catch (err) {
    console.error("Falha ao encerrar sessão:", err.message);
    return responderErro(res, 503, "Sessão indisponível.");
  }
}

module.exports = { login, logout };
