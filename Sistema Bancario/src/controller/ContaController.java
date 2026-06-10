package controller;

import dao.*;
import model.*;
import java.sql.*;

public class ContaController {

    private final ContaCorrenteDAO contaCorrenteDAO = new ContaCorrenteDAOImpl();
    private final ContaInvestimentoDAO contaInvestimentoDAO = new ContaInvestimentoDAOImpl();

    public void criarContaCorrente(ContaCorrente cc) {
        contaCorrenteDAO.inserir(cc);
    }

    public void criarContaInvestimento(ContaInvestimento ci) {
        contaInvestimentoDAO.inserir(ci);
    }

    public void atualizarConta(Conta conta) {
        if (conta instanceof ContaCorrente) {
            contaCorrenteDAO.atualizar((ContaCorrente) conta);
        } else if (conta instanceof ContaInvestimento) {
            contaInvestimentoDAO.atualizar((ContaInvestimento) conta);
        }
    }

    public int proximoNumeroConta() {
        String sql = "SELECT COALESCE(MAX(numero), 1000) FROM (" +
                     "SELECT numero FROM conta_corrente " +
                     "UNION ALL " +
                     "SELECT numero FROM conta_investimento) t";
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1) + 1;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao gerar número de conta: " + e.getMessage(), e);
        }
        return 1001;
    }
}
