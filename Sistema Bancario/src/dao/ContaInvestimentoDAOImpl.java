package dao;

import model.Cliente;
import model.ContaInvestimento;
import java.sql.*;

public class ContaInvestimentoDAOImpl implements ContaInvestimentoDAO {

    @Override
    public void inserir(ContaInvestimento c) {
        String sql = "INSERT INTO conta_investimento (numero, saldo, montante_minimo, deposito_minimo, cpf_cliente) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, c.getNumero());
            ps.setDouble(2, c.getSaldo());
            ps.setDouble(3, c.getMontanteMinimo());
            ps.setDouble(4, c.getDepositoMinimo());
            ps.setString(5, c.getDono().getCpf());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inserir conta investimento: " + e.getMessage(), e);
        }
    }

    @Override
    public void atualizar(ContaInvestimento c) {
        String sql = "UPDATE conta_investimento SET saldo=?, montante_minimo=?, deposito_minimo=? WHERE numero=?";
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, c.getSaldo());
            ps.setDouble(2, c.getMontanteMinimo());
            ps.setDouble(3, c.getDepositoMinimo());
            ps.setInt(4, c.getNumero());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar conta investimento: " + e.getMessage(), e);
        }
    }

    @Override
    public void excluirPorCpf(String cpf) {
        String sql = "DELETE FROM conta_investimento WHERE cpf_cliente=?";
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cpf);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir conta investimento: " + e.getMessage(), e);
        }
    }

    @Override
    public ContaInvestimento buscarPorCpf(String cpf, Cliente dono) {
        String sql = "SELECT * FROM conta_investimento WHERE cpf_cliente=?";
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cpf);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return ContaInvestimento.fromDB(
                        dono,
                        rs.getInt("numero"),
                        rs.getDouble("saldo"),
                        rs.getDouble("montante_minimo"),
                        rs.getDouble("deposito_minimo")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar conta investimento: " + e.getMessage(), e);
        }
        return null;
    }
}
