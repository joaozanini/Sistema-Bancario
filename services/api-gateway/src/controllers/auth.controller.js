const authService = require('../services/auth.service');

async function login(req, res) {
  const { email, senha } = req.body;
  if (!email || !senha) {
    return res.status(400).json({ auth: false, message: 'E-mail e senha são obrigatórios.' });
  }

  try {
    const { token, tipo, usuario } = await authService.autenticar(email, senha);
    return res.json({ auth: true, token, tipo, usuario });
  } catch (err) {
    if (err instanceof authService.CredenciaisInvalidasError) {
      return res.status(401).json({ auth: false, message: 'Login inválido!' });
    }
    console.error('Falha no login (API Composition):', err.message);
    return res.status(502).json({ auth: false, message: 'Serviço de autenticação indisponível.' });
  }
}

async function logout(req, res) {
  const { jti, exp } = req.tokenMeta;
  const { cpf } = req.user;

  try {
    await authService.encerrarSessao(jti, cpf, exp);
    return res.status(200).json({ auth: false });
  } catch (err) {
    console.error('Falha ao encerrar sessão:', err.message);
    return res.status(503).json({ auth: false, message: 'Sessão indisponível.' });
  }
}

module.exports = { login, logout };
