const sagaService = require("../services/saga.service");
const gerenteService = require("../services/gerente.service");
const gerentesService = require("../services/gerentes.service");
const { responderErro } = require("../services/erro.service");
const { responderJobAceito } = require("./jobs.controller");

// R12: a lista vai dentro de { gerentes }, com os _links da própria listagem
async function listar(req, res) {
  try {
    const gerentes = await gerentesService.listarTodos();
    return res.json({ gerentes, _links: { self: { href: "/gerentes" } } });
  } catch (err) {
    console.error("Falha ao listar gerentes (API Composition):", err.message);
    return responderErro(res, 502, "Serviço de gerentes indisponível.");
  }
}

// R14: a tela de edicao carrega o gerente por aqui antes de enviar o PUT.
// Passa pelo cache-aside, como a composicao do login.
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

// R13: a senha segue no payload da SAGA e o Orquestrador a mantém só em memória
// até o passo do MS Auth. Aqui só se confere o formato da requisição (400); e-mail
// ou CPF já usados são regra de negócio e aparecem depois, como job FALHA.
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
    // R13 grava o gerente: o enunciado (5.5) pede invalidar o cadastro em cache
    await gerenteService.invalidar(cpf);
    return responderJobAceito(res, job);
  } catch (err) {
    console.error("Falha ao iniciar a SAGA de inserção:", err.message);
    return responderErro(res, 502, "Não foi possível iniciar a inserção.");
  }
}

// R15: o Gateway recusa a auto-remocao ANTES de publicar, senao a SAGA chegaria
// a inativar o gerente e so depois seria desfeita por compensacao.
async function remover(req, res) {
  const { cpf } = req.params;

  if (req.user.cpf === cpf) {
    return responderErro(res, 403, "Um gerente não pode remover a si mesmo.");
  }

  try {
    const job = await sagaService.iniciar(TIPO_REMOVER_GERENTE, DOMINIO, {
      cpf,
    });
    // R15 inativa o gerente: o cadastro em cache deixa de valer
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
