package dao;

import model.Cliente;
import model.ContaCorrente;

public interface ContaCorrenteDAO {
    void inserir(ContaCorrente conta);
    void atualizar(ContaCorrente conta);
    void excluirPorCpf(String cpfCliente);
    ContaCorrente buscarPorCpf(String cpfCliente, Cliente dono);
}
