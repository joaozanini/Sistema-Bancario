const contaService = require('./conta.service');
const clienteService = require('./cliente.service');

async function comporCliente(conta) {
  const cliente = await clienteService.buscar(conta.cpfCliente);
  return {
    cpf: cliente.cpf,
    nome: cliente.nome,
    cidade: cliente.cidade,
    uf: cliente.uf,
    saldo: conta.saldo,
    _links: cliente._links,
  };
}

// R11: o saldo vem do MS Conta e os dados cadastrais do MS Cliente
async function listarTodos() {
  const contas = await contaService.listar();
  const clientes = await Promise.all(contas.map(comporCliente));
  return clientes.sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
}

module.exports = { listarTodos };
