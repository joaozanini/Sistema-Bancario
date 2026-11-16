const sagaService = require("../services/saga.service");
const gerenteService = require("../services/gerente.service");
const gerentesService = require("../services/gerentes.service");
const { responderErro } = require("../services/erro.service");
const { responderJobAceito } = require("./jobs.controller");

// R12: lista os gerentes em { gerentes } com _links
async function listar(req, res) {
  try {
    const gerentes = await gerentesService.listarTodos();
    return res.json({ gerentes, _links: { self: { href: "/gerentes" } } });
  } catch (err) {
    console.error("Falha ao listar gerentes (API Composition):", err.message);
    return responderErro(res, 502, "Serviço de gerentes indisponível.");
  }
}

// R14: busca o gerente pelo CPF via cache-aside
async function buscar(req, res) {
  const { cpf } = req.params;

  try {
    return res.json(await gerenteService.buscar(cpf));
  } catch (err) {
    if (err instanceof gerenteService.GerenteNaoEncontradoError) {
      return responderErro(res, 404, "Gerente não encontrado.");
    }
    console.error("Falha ao consultar gerente no MS Gerente:", err.message);
    return responderErro(res, 502, "Serviço de gerentes indisponível.");
  }
}

const TIPO_INSERIR_GERENTE = "inserir-gerente";
const TIPO_REMOVER_GERENTE = "remover-gerente";
const DOMINIO = "gerentes";
const CAMPOS_OBRIGATORIOS = ["cpf", "nome", "email", "telefone", "senha"];

// R13: valida o corpo e inicia a SAGA de inserção de gerente
async function inserir(req, res) {
  const { cpf, nome, email, telefone, senha } = req.body || {};

  const faltando = CAMPOS_OBRIGATORIOS.filter(
    (campo) => !String(req.body?.[campo] ?? "").trim(),
  );
  if (faltando.length > 0) {
    return responderErro(res, 400, `Campos obrigatórios: ${faltando.join(", ")}.`);
  }
  if (!/^\d{11}$/.test(String(cpf))) {
    return responderErro(res, 400, "CPF deve conter 11 dígitos, sem pontuação.");
  }

  try {
    const job = await sagaService.iniciar(TIPO_INSERIR_GERENTE, DOMINIO, {
      cpf,
      nome,
      email,
      telefone,
      senha,
    });
    // invalida o cache do gerente
    await gerenteService.invalidar(cpf);
    return responderJobAceito(res, job);
  } catch (err) {
    console.error("Falha ao iniciar a SAGA de inserção:", err.message);
    return responderErro(res, 502, "Não foi possível iniciar a inserção.");
  }
}

// R15: recusa a auto-remoção e inicia a SAGA de remoção de gerente
async function remover(req, res) {
  const { cpf } = req.params;

  if (req.user.cpf === cpf) {
    return responderErro(res, 403, "Um gerente não pode remover a si mesmo.");
  }

  try {
    const job = await sagaService.iniciar(TIPO_REMOVER_GERENTE, DOMINIO, {
      cpf,
    });
    // invalida o cache do gerente
    await gerenteService.invalidar(cpf);
    return responderJobAceito(res, job);
  } catch (err) {
    console.error("Falha ao iniciar a SAGA de remoção:", err.message);
    return responderErro(res, 502, "Não foi possível iniciar a remoção.");
  }
}

module.exports = {
  listar,
  buscar,
  inserir,
  remover,
  TIPO_INSERIR_GERENTE,
  TIPO_REMOVER_GERENTE,
  DOMINIO,
};
