const contaService = require('./conta.service');
const clienteService = require('./cliente.service');

async function comporCliente(conta) {
  const cliente = await clienteService.buscar(conta.cpfCliente);
  return {
    cpf: cliente.cpf,
    nome: cliente.nome,
    cidade: cliente.endereco?.cidade ?? null,
    estado: cliente.endereco?.uf ?? null,
    saldo: conta.saldo,
    _links: cliente._links,
  };
}

// sem acento e em minúsculas: "coan" tem de achar "Coândrya"
function normalizar(texto) {
  return String(texto ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}

// R11: pesquisa por nome (ou parte) ou por CPF (ou parte). O CPF só entra na
// comparação quando o termo é feito de dígitos e pontuação de CPF: num termo como
// "Cliente Novo 1" o dígito faz parte do nome e não pode casar com todo CPF que tenha "1".
function filtrar(clientes, busca) {
  const termo = normalizar(busca);
  if (!termo) {
    return clientes;
  }

  const digitos = /^[\d.\-\s]+$/.test(termo) ? termo.replace(/\D/g, '') : '';
  return clientes.filter(
    (cliente) => normalizar(cliente.nome).includes(termo) || (digitos !== '' && cliente.cpf.includes(digitos)),
  );
}

// R11: o saldo vem do MS Conta e os dados cadastrais do MS Cliente
async function listarTodos(busca) {
  const contas = await contaService.listar();
  const clientes = await Promise.all(contas.map(comporCliente));
  return filtrar(clientes, busca).sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
}

module.exports = { listarTodos, filtrar };
