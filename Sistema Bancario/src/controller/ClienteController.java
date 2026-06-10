package controller;

import dao.*;
import model.*;
import java.util.List;

public class ClienteController {

    private final ClienteDAO clienteDAO = new ClienteDAOImpl();
    private final ContaCorrenteDAO contaCorrenteDAO = new ContaCorrenteDAOImpl();
    private final ContaInvestimentoDAO contaInvestimentoDAO = new ContaInvestimentoDAOImpl();

    public List<Cliente> listarClientes() {
        List<Cliente> clientes = clienteDAO.listarTodos();
        for (Cliente c : clientes) {
            c.setConta(carregarConta(c));
        }
        return clientes;
    }

    public Cliente buscarPorCpf(String cpf) {
        Cliente c = clienteDAO.buscarPorCpf(cpf);
        if (c != null) c.setConta(carregarConta(c));
        return c;
    }

    public Cliente buscarPorRg(String rg) {
        Cliente c = clienteDAO.buscarPorRg(rg);
        if (c != null) c.setConta(carregarConta(c));
        return c;
    }

    public void incluir(Cliente c) {
        clienteDAO.inserir(c);
    }

    public void atualizar(Cliente c) {
        clienteDAO.atualizar(c);
    }

    public void excluir(Cliente c) {
        clienteDAO.excluir(c.getCpf()); // ON DELETE CASCADE remove as contas vinculadas
    }

    private Conta carregarConta(Cliente c) {
        ContaCorrente cc = contaCorrenteDAO.buscarPorCpf(c.getCpf(), c);
        if (cc != null) return cc;
        return contaInvestimentoDAO.buscarPorCpf(c.getCpf(), c);
    }
}
