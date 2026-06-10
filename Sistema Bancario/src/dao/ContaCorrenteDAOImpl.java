package dao;

import model.Cliente;
import model.ContaCorrente;
import java.sql.*;

public class ContaCorrenteDAOImpl implements ContaCorrenteDAO {

    @Override
    public void inserir(ContaCorrente c) {
        String sql = "INSERT INTO conta_corrente (numero, saldo, limite, cpf_cliente) VALUES (?, ?, ?, ?)";
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, c.getNumero());
            ps.setDouble(2, c.getSaldo());
            ps.setDouble(3, c.getLimite());
            ps.setString(4, c.getDono().getCpf());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inserir conta corrente: " + e.getMessage(), e);
        }
    }

    @Override
    public void atualizar(ContaCorrente c) {
        String sql = "UPDATE conta_corrente SET saldo=?, limite=? WHERE numero=?";
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, c.getSaldo());
            ps.setDouble(2, c.getLimite());
            ps.setInt(3, c.getNumero());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar conta corrente: " + e.getMessage(), e);
        }
    }

    @Override
    public void excluirPorCpf(String cpf) {
        String sql = "DELETE FROM conta_corrente WHERE cpf_cliente=?";
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cpf);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir conta corrente: " + e.getMessage(), e);
        }
    }

    @Override
    public ContaCorrente buscarPorCpf(String cpf, Cliente dono) {
        String sql = "SELECT * FROM conta_corrente WHERE cpf_cliente=?";
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cpf);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return ContaCorrente.fromDB(
                        dono,
                        rs.getInt("numero"),
                        rs.getDouble("saldo"),
                        rs.getDouble("limite")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar conta corrente: " + e.getMessage(), e);
        }
        return null;
    }
}
