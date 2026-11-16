const tokenService = require('../services/token.service');
const sessionService = require('../services/session.service');
const { responderErro } = require('../services/erro.service');

// valida o JWT e a sessão no Redis e renova o TTL da sessão
async function verifyJwtAndSession(req, res, next) {
  const token = req.headers['x-access-token'];
  if (!token) {
    return res.status(401).json({ auth: false, message: 'Token não fornecido.' });
  }

  let payload;
  try {
    payload = tokenService.verifyToken(token);
  } catch {
    return res.status(401).json({ auth: false, message: 'Falha ao autenticar o token.' });
  }

  const { jti, cpf, tipo, exp } = payload;

  try {
    if (await sessionService.isRevoked(jti)) {
      return res.status(401).json({ auth: false, message: 'Falha ao autenticar o token.' });
    }
    const sessao = await sessionService.getSession(jti);
    if (!sessao) {
      return res.status(401).json({ auth: false, message: 'Falha ao autenticar o token.' });
    }
    await sessionService.renewSession(jti, cpf);
  } catch (err) {
    console.error('Falha ao consultar sessão no Redis:', err.message);
    return responderErro(res, 503, 'Sessão indisponível.');
  }

  req.user = { cpf, tipo };
  req.tokenMeta = { jti, exp };
  next();
}

// remove os headers de identidade vindos do cliente HTTP
function descartarIdentidadeDoCliente(req, res, next) {
  delete req.headers['x-user-cpf'];
  delete req.headers['x-user-tipo'];
  next();
}

// injeta X-User-CPF e X-User-Tipo a partir do token
function injectUserHeaders(req, res, next) {
  req.headers['x-user-cpf'] = req.user.cpf;
  req.headers['x-user-tipo'] = req.user.tipo;
  next();
}

function exigirTipo(tipoPermitido) {
  return (req, res, next) => {
    if (String(req.user.tipo).toUpperCase() !== tipoPermitido) {
      return responderErro(res, 403, 'Acesso negado: operação exclusiva do perfil ' + tipoPermitido + '.');
    }
    return next();
  };
}

module.exports = { verifyJwtAndSession, descartarIdentidadeDoCliente, injectUserHeaders, exigirTipo };
