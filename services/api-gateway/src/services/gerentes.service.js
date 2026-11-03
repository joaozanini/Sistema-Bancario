const contaService = require('./conta.service');
const gerenteService = require('./gerente.service');

function contarClientesPorGerente(contas) {
  const contagem = new Map();
  for (const { gerenteCpf } of contas) {
    contagem.set(gerenteCpf, (contagem.get(gerenteCpf) ?? 0) + 1);
  }
  return contagem;
}

// R12: os dados cadastrais vêm do MS Gerente e a quantidade de clientes das
// contas do MS Conta (gerente sem nenhuma conta tem 0 clientes)
async function listarTodos() {
  const [gerentes, contas] = await Promise.all([gerenteService.listarAtivos(), contaService.listar()]);
  const clientesPorGerente = contarClientesPorGerente(contas);

  return gerentes
    .map((gerente) => ({
      nome: gerente.nome,
      cpf: gerente.cpf,
      email: gerente.email,
      telefone: gerente.telefone,
      quantidadeClientes: clientesPorGerente.get(gerente.cpf) ?? 0,
      _links: gerente._links,
    }))
    .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
}

module.exports = { listarTodos };
