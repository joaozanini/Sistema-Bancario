package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import model.*;
import Utils.ButtonColumn;

public class TelaManterClientes extends JFrame {

    private JTable tabelaClientes;
    private ClienteTableModel tableModel;
    private JTextField txtFiltro;

    public TelaManterClientes() {
        setTitle("Dashboard de Clientes do Banco");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 800, 600);

        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(contentPane);

        JPanel painelFiltro = new JPanel(new FlowLayout(FlowLayout.LEFT));
        txtFiltro = new JTextField(20);
        JButton btnFiltrarNome = new JButton("Filtrar por Nome");
        JButton btnFiltrarCPF = new JButton("Filtrar por CPF");
        JButton btnLimparFiltro = new JButton("Limpar");
        JButton btnOrdenarSalario = new JButton("Ordenar por Salário");

        painelFiltro.add(new JLabel("Filtro:"));
        painelFiltro.add(txtFiltro);
        painelFiltro.add(btnFiltrarNome);
        painelFiltro.add(btnFiltrarCPF);
        painelFiltro.add(btnLimparFiltro);
        painelFiltro.add(btnOrdenarSalario);
        contentPane.add(painelFiltro, BorderLayout.NORTH);

        tableModel = new ClienteTableModel();
        tabelaClientes = new JTable(tableModel);

        TableRowSorter<ClienteTableModel> sorter = new TableRowSorter<>(tableModel);
        tabelaClientes.setRowSorter(sorter);
        tabelaClientes.setAutoCreateRowSorter(true);

        JScrollPane scrollPane = new JScrollPane(tabelaClientes);
        contentPane.add(scrollPane, BorderLayout.CENTER);

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton btnIncluir = new JButton("Incluir Cliente");
        JButton btnExcluir = new JButton("Excluir Selecionado");

        painelBotoes.add(btnIncluir);
        painelBotoes.add(btnExcluir);
        contentPane.add(painelBotoes, BorderLayout.SOUTH);

        btnIncluir.addActionListener(e -> abrirTelaCadastroInclusao());
        btnExcluir.addActionListener(e -> excluirCliente());

        btnFiltrarNome.addActionListener(e -> tableModel.filtrarPorNome(txtFiltro.getText()));
        btnFiltrarCPF.addActionListener(e -> tableModel.filtrarPorCpf(txtFiltro.getText()));
        btnLimparFiltro.addActionListener(e -> {
            txtFiltro.setText("");
            tableModel.filtrarPorNome(""); // Recarrega a lista original
        });

        btnOrdenarSalario.addActionListener(e -> {
            sorter.setComparator(0, new ClienteSalarioComparator());

            List<RowSorter.SortKey> sortKeys = new ArrayList<>();
            sortKeys.add(new RowSorter.SortKey(0, SortOrder.DESCENDING));
            sorter.setSortKeys(sortKeys);

            sorter.sort();

            JOptionPane.showMessageDialog(this, "Tabela ordenada por Salário (do maior para o menor).", "Ordenação", JOptionPane.INFORMATION_MESSAGE);

            sorter.setComparator(0, null);
        });

        ActionListener acaoEditar = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int linha = Integer.parseInt(e.getActionCommand());
                abrirTelaCadastroAtualizacao(linha);
            }
        };
        new ButtonColumn(tabelaClientes, acaoEditar, 5);

        ActionListener acaoVincular = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int linha = Integer.parseInt(e.getActionCommand());
                abrirTelaVincularConta(linha);
            }
        };
        new ButtonColumn(tabelaClientes, acaoVincular, 6);

        ActionListener acaoConta = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int linha = Integer.parseInt(e.getActionCommand());
                abrirTelaOperacoesConta(linha);
            }
        };
        new ButtonColumn(tabelaClientes, acaoConta, 7);

        atualizarTabela();
    }

    private void abrirTelaCadastroInclusao() {
        TelaCadastroCliente telaCadastro = new TelaCadastroCliente(this, null);
        telaCadastro.setLocationRelativeTo(this);
        telaCadastro.setVisible(true);

        atualizarTabela();
    }

    private void abrirTelaCadastroAtualizacao(int indiceModelo) {
        Cliente clienteParaAtualizar = tableModel.getCliente(indiceModelo);
        if (clienteParaAtualizar == null) return;

        TelaCadastroCliente telaCadastro = new TelaCadastroCliente(this, clienteParaAtualizar);
        telaCadastro.setLocationRelativeTo(this);
        telaCadastro.setVisible(true);

        atualizarTabela();
    }

    private void abrirTelaVincularConta(int indiceModelo) {
        Cliente clienteSelecionado = tableModel.getCliente(indiceModelo);
        if (clienteSelecionado == null) return;

        TelaVincularConta telaVincular = new TelaVincularConta(this, clienteSelecionado);
        telaVincular.setLocationRelativeTo(this);
        telaVincular.setVisible(true);

        atualizarTabela();
    }

    private void abrirTelaOperacoesConta(int indiceModelo) {
        Cliente clienteSelecionado = tableModel.getCliente(indiceModelo);

        if (clienteSelecionado == null) return;

        Conta conta = clienteSelecionado.getConta();

        if (conta == null) {
            JOptionPane.showMessageDialog(this,
                    "O cliente " + clienteSelecionado.getNome() + " não possui conta vinculada. Use o botão 'Vincular' primeiro.",
                    "Erro de Operação", JOptionPane.WARNING_MESSAGE);
            return;
        }

        TelaOperacoesConta telaOperacoes = new TelaOperacoesConta(this, conta);
        telaOperacoes.setLocationRelativeTo(this);
        telaOperacoes.setVisible(true);

        atualizarTabela();
    }

    private void excluirCliente() {
        int linhaSelecionada = tabelaClientes.getSelectedRow();
        if (linhaSelecionada == -1) {
            JOptionPane.showMessageDialog(this,
                    "Selecione um cliente na tabela para excluir.",
                    "Nenhum cliente selecionado",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int indiceModelo = tabelaClientes.convertRowIndexToModel(linhaSelecionada);
        Cliente clienteParaExcluir = tableModel.getCliente(indiceModelo);

        String msg = "Tem certeza que deseja excluir o cliente: "
                + clienteParaExcluir.getNome()
                + "?\nTodas as contas vinculadas a ele serão apagadas.";

        int resposta = JOptionPane.showConfirmDialog(this, msg, "Confirmar Exclusão",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (resposta == JOptionPane.YES_OPTION) {
            RepositorioDados.getInstance().removerCliente(clienteParaExcluir);
            atualizarTabela();
            JOptionPane.showMessageDialog(this, "Cliente excluído com sucesso.");
        }
    }


    public void atualizarTabela() {
        List<Cliente> clientes = RepositorioDados.getInstance().getListaClientes();
        tableModel.setClientes(clientes);
    }
}

