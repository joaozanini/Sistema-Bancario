const contaService = require('./conta.service');
const clienteService = require('./cliente.service');
const gerenteService = require('./gerente.service');

// nome do gerente, ou null se a conta não tiver gerente
async function nomeDoGerente(cpf) {
  if (!cpf) {
    return null;
  }

  const { nome } = await gerenteService.buscar(cpf);
  return nome;
}

// R16: monta a linha com dados da conta, do cliente e do gerente
async function comporLinha(conta) {
  const [cliente, nomeGerente] = await Promise.all([
    clienteService.buscar(conta.cpfCliente),
    nomeDoGerente(conta.cpfGerente),
  ]);

  return {
    cpf: cliente.cpf,
    nome: cliente.nome,
    email: cliente.email,
    salario: cliente.salario,
    numeroConta: conta.numero,
    saldo: conta.saldo,
    cpfGerente: conta.cpfGerente ?? null,
    nomeGerente,
  };
}

async function gerar() {
  const contas = await contaService.listar();
  const linhas = await Promise.all(contas.map(comporLinha));
  return linhas.sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
}

module.exports = { gerar };
