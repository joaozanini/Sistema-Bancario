package dao;

import model.Cliente;
import model.ContaInvestimento;

public interface ContaInvestimentoDAO {
    void inserir(ContaInvestimento conta);
    void atualizar(ContaInvestimento conta);
    void excluirPorCpf(String cpfCliente);
    ContaInvestimento buscarPorCpf(String cpfCliente, Cliente dono);
}
