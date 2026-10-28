const sagaService = require('../services/saga.service');
const gerenteService = require('../services/gerente.service');

const TIPO_INSERIR_GERENTE = 'inserir-gerente';
const TIPO_REMOVER_GERENTE = 'remover-gerente';
const DOMINIO = 'gerentes';
const CAMPOS_OBRIGATORIOS = ['cpf', 'nome', 'email', 'senha'];

// R13: a senha segue no payload da SAGA e o Orquestrador a mantém só em memória
// até o passo do MS Auth
async function inserir(req, res) {
  const { cpf, nome, email, telefone, senha } = req.body || {};

  const faltando = CAMPOS_OBRIGATORIOS.filter((campo) => !String(req.body?.[campo] ?? '').trim());
  if (faltando.length > 0) {
    return res.status(400).json({ message: `Campos obrigatórios: ${faltando.join(', ')}.` });
  }

  try {
    const jobId = await sagaService.iniciar(TIPO_INSERIR_GERENTE, DOMINIO, { cpf, nome, email, telefone, senha });
    return res.status(202).json({ jobId });
  } catch (err) {
    console.error('Falha ao iniciar a SAGA de inserção:', err.message);
    return res.status(502).json({ message: 'Não foi possível iniciar a inserção.' });
  }
}

// R15: o Gateway recusa a auto-remocao ANTES de publicar, senao a SAGA chegaria
// a inativar o gerente e so depois seria desfeita por compensacao.
async function remover(req, res) {
  const { cpf } = req.params;

  if (req.user.cpf === cpf) {
    return res.status(403).json({ message: 'Um gerente não pode remover a si mesmo.' });
  }

  try {
    const jobId = await sagaService.iniciar(TIPO_REMOVER_GERENTE, DOMINIO, { cpf });
    // R15 inativa o gerente: o cadastro em cache deixa de valer
    await gerenteService.invalidar(cpf);
    return res.status(202).json({ jobId });
  } catch (err) {
    console.error('Falha ao iniciar a SAGA de remoção:', err.message);
    return res.status(502).json({ message: 'Não foi possível iniciar a remoção.' });
  }
}

module.exports = { inserir, remover, TIPO_INSERIR_GERENTE, TIPO_REMOVER_GERENTE, DOMINIO };
