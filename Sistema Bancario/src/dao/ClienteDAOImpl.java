package dao;

import model.Cliente;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ClienteDAOImpl implements ClienteDAO {

    @Override
    public void inserir(Cliente c) {
        String sql = "INSERT INTO clientes (cpf, nome, sobrenome, rg, endereco) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getCpf());
            ps.setString(2, c.getNome());
            ps.setString(3, c.getSobrenome());
            ps.setString(4, c.getRg());
            ps.setString(5, c.getEndereco());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao inserir cliente: " + e.getMessage(), e);
        }
    }

    @Override
    public void atualizar(Cliente c) {
        String sql = "UPDATE clientes SET nome=?, sobrenome=?, rg=?, endereco=? WHERE cpf=?";
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getNome());
            ps.setString(2, c.getSobrenome());
            ps.setString(3, c.getRg());
            ps.setString(4, c.getEndereco());
            ps.setString(5, c.getCpf());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar cliente: " + e.getMessage(), e);
        }
    }

    @Override
    public void excluir(String cpf) {
        String sql = "DELETE FROM clientes WHERE cpf=?";
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cpf);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir cliente: " + e.getMessage(), e);
        }
    }

    @Override
    public Cliente buscarPorCpf(String cpf) {
        String sql = "SELECT * FROM clientes WHERE cpf=?";
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cpf);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapear(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar cliente por CPF: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public Cliente buscarPorRg(String rg) {
        String sql = "SELECT * FROM clientes WHERE rg=?";
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rg);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapear(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar cliente por RG: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<Cliente> listarTodos() {
        String sql = "SELECT * FROM clientes ORDER BY nome, sobrenome";
        List<Cliente> lista = new ArrayList<>();
        try (Connection conn = ConexaoDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar clientes: " + e.getMessage(), e);
        }
        return lista;
    }

    private Cliente mapear(ResultSet rs) throws SQLException {
        return new Cliente(
            rs.getString("nome"),
            rs.getString("sobrenome"),
            rs.getString("rg"),
            rs.getString("cpf"),
            rs.getString("endereco")
        );
    }
}
