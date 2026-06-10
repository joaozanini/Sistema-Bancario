package dao;

import model.Cliente;
import java.util.List;

public interface ClienteDAO {
    void inserir(Cliente cliente);
    void atualizar(Cliente cliente);
    void excluir(String cpf);
    Cliente buscarPorCpf(String cpf);
    Cliente buscarPorRg(String rg);
    List<Cliente> listarTodos();
}
