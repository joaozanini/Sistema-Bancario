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

// remove acentos e converte para minúsculas
function normalizar(texto) {
  return String(texto ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}

// R11: filtra por parte do nome ou, se o termo for numérico, por parte do CPF
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

// R11: compõe os clientes com o saldo do MS Conta e os dados do MS Cliente
async function listarTodos(busca) {
  const contas = await contaService.listar();
  const clientes = await Promise.all(contas.map(comporCliente));
  return filtrar(clientes, busca).sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
}

module.exports = { listarTodos, filtrar };
