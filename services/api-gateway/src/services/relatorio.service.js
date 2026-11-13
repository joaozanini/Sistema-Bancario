const contaService = require('./conta.service');
const clienteService = require('./cliente.service');
const gerenteService = require('./gerente.service');

// Uma conta sem gerente não deveria existir depois do R9, mas se existir o
// relatório inteiro não pode cair por causa de uma linha.
async function nomeDoGerente(cpf) {
  if (!cpf) {
    return null;
  }

  const { nome } = await gerenteService.buscar(cpf);
  return nome;
}

// R16: os oito campos vêm de três serviços que não se conhecem — a junção é aqui
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
