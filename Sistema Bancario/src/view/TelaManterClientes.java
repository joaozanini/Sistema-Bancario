package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Comparator;

import model.*;
import Utils.ButtonColumn; // classe customizada para colocar botões dentro da JTable

public class TelaManterClientes extends JFrame { // JFrame principal

    private JTable tabelaClientes;
    private ClienteTableModel tableModel;
    private JTextField txtFiltro;

    public TelaManterClientes() {
        setTitle("Dashboard de Clientes do Banco");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 1100, 600); // Aumentei a largura para acomodar a nova coluna

        JPanel contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(contentPane);

        // PAINEL FILTRO
        JPanel painelFiltro = new JPanel(new FlowLayout(FlowLayout.LEFT));
        txtFiltro = new JTextField(20);
        JButton btnFiltrarNome = new JButton("Filtrar por Nome");
        JButton btnFiltrarSobrenome = new JButton("Filtrar por Sobrenome");
        JButton btnFiltrarCPF = new JButton("Filtrar por CPF");
        JButton btnFiltrarRG = new JButton("Filtrar por RG");
        JButton btnLimparFiltro = new JButton("Limpar");

        painelFiltro.add(new JLabel("Filtro:"));
        painelFiltro.add(txtFiltro);
        painelFiltro.add(btnFiltrarNome);
        painelFiltro.add(btnFiltrarSobrenome);
        painelFiltro.add(btnFiltrarCPF);
        painelFiltro.add(btnFiltrarRG);
        painelFiltro.add(btnLimparFiltro);
        contentPane.add(painelFiltro, BorderLayout.NORTH);

        // TABELA
        tableModel = new ClienteTableModel();
        tabelaClientes = new JTable(tableModel);

        TableRowSorter<ClienteTableModel> sorter = new TableRowSorter<>(tableModel); // permite ordenar e filtrar dados da tabela
        
        // CONFIG COMPARADORES COLUNAS
        configurarComparadores(sorter);
        
        tabelaClientes.setRowSorter(sorter);
        tabelaClientes.setAutoCreateRowSorter(false);

        JScrollPane scrollPane = new JScrollPane(tabelaClientes); // painel com barras de rolagem
        contentPane.add(scrollPane, BorderLayout.CENTER);

        // BOTÕES INFERIORES
        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton btnIncluir = new JButton("Incluir Cliente");
        JButton btnExcluir = new JButton("Excluir Selecionado");

        painelBotoes.add(btnIncluir);
        painelBotoes.add(btnExcluir);
        contentPane.add(painelBotoes, BorderLayout.SOUTH);

        // AÇÕES DOS BOTÕES
        btnIncluir.addActionListener(e -> abrirTelaCadastroInclusao());

        btnExcluir.addActionListener(e -> excluirCliente());

        btnFiltrarNome.addActionListener(e -> tableModel.filtrarPorNome(txtFiltro.getText()));
        btnFiltrarSobrenome.addActionListener(e -> tableModel.filtrarPorSobrenome(txtFiltro.getText()));
        btnFiltrarCPF.addActionListener(e -> tableModel.filtrarPorCpf(txtFiltro.getText()));
        btnFiltrarRG.addActionListener(e -> tableModel.filtrarPorRg(txtFiltro.getText()));

        btnLimparFiltro.addActionListener(e -> {
            txtFiltro.setText("");
            tableModel.recarregar();
        });

        // BOTÃO EDITAR
        ActionListener acaoEditar = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("BOTÃO EDITAR CLICADO - ActionCommand: " + e.getActionCommand());
                try {
                    int linhaView = Integer.parseInt(e.getActionCommand());
                    if (linhaView >= 0) {
                        int linhaModelo = tabelaClientes.convertRowIndexToModel(linhaView); // converte índice da view para o modelo
                        System.out.println("Abrindo edição para linha modelo: " + linhaModelo);
                        abrirTelaCadastroAtualizacao(linhaModelo);
                    } else {
                        System.out.println("Linha inválida: " + linhaView);
                    }
                } catch (NumberFormatException ex) {
                    System.out.println("Erro ao converter linha: " + e.getActionCommand());
                }
            }
        };
        new ButtonColumn(tabelaClientes, acaoEditar, 7);

        // BOTÃO VINCULAR
        ActionListener acaoVincular = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("BOTÃO VINCULAR CLICADO - ActionCommand: " + e.getActionCommand());
                try {
                    int linhaView = Integer.parseInt(e.getActionCommand());
                    if (linhaView >= 0) {
                        int linhaModelo = tabelaClientes.convertRowIndexToModel(linhaView);
                        System.out.println("Abrindo vinculação para linha modelo: " + linhaModelo);
                        abrirTelaVincularConta(linhaModelo);
                    } else {
                        System.out.println("Linha inválida: " + linhaView);
                    }
                } catch (NumberFormatException ex) {
                    System.out.println("Erro ao converter linha: " + e.getActionCommand());
                }
            }
        };
        new ButtonColumn(tabelaClientes, acaoVincular, 8);

        // BOTÃO OPERAÇÕES DA CONTA
        ActionListener acaoConta = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("BOTÃO OPERAR CLICADO - ActionCommand: " + e.getActionCommand());
                try {
                    int linhaView = Integer.parseInt(e.getActionCommand());
                    if (linhaView >= 0) {
                        int linhaModelo = tabelaClientes.convertRowIndexToModel(linhaView);
                        System.out.println("Abrindo operações para linha modelo: " + linhaModelo);
                        abrirTelaOperacoesConta(linhaModelo);
                    } else {
                        System.out.println("Linha inválida: " + linhaView);
                    }
                } catch (NumberFormatException ex) {
                    System.out.println("Erro ao converter linha: " + e.getActionCommand());
                }
            }
        };
        new ButtonColumn(tabelaClientes, acaoConta, 9);

        atualizarTabela();
    }

    // ORDENAÇÃO COLUNAS
    
    private void configurarComparadores(TableRowSorter<ClienteTableModel> sorter) {
        // Coluna 0: Nome
        sorter.setComparator(0, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                return s1.compareToIgnoreCase(s2);
            }
        });
        
        // Coluna 1: Sobrenome
        sorter.setComparator(1, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                return s1.compareToIgnoreCase(s2);
            }
        });
        
        // Coluna 2: RG
        sorter.setComparator(2, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                try {
                    String rg1Limpo = s1.replaceAll("[^0-9]", "");
                    String rg2Limpo = s2.replaceAll("[^0-9]", "");
                    long rg1 = Long.parseLong(rg1Limpo);
                    long rg2 = Long.parseLong(rg2Limpo);
                    return Long.compare(rg1, rg2);
                } catch (NumberFormatException e) {
                    return s1.compareTo(s2);
                }
            }
        });
        
        // Coluna 3: CPF
        sorter.setComparator(3, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                try {
                    String cpf1Limpo = s1.replaceAll("[^0-9]", "");
                    String cpf2Limpo = s2.replaceAll("[^0-9]", "");
                    long cpf1 = Long.parseLong(cpf1Limpo);
                    long cpf2 = Long.parseLong(cpf2Limpo);
                    return Long.compare(cpf1, cpf2);
                } catch (NumberFormatException e) {
                    return s1.compareTo(s2);
                }
            }
        });
        
        // Coluna 4: Endereço
        sorter.setComparator(4, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                return s1.compareToIgnoreCase(s2);
            }
        });
        
        // Coluna 5: Saldo
        sorter.setComparator(5, new Comparator<Object>() {
            @Override
            public int compare(Object obj1, Object obj2) {
                try {
                    double valor1 = extrairValorSaldo(obj1);
                    double valor2 = extrairValorSaldo(obj2);
                    return Double.compare(valor1, valor2);
                } catch (Exception ex) {
                    return 0;
                }
            }
            
            private double extrairValorSaldo(Object valorObj) {
                if (valorObj == null) return 0.0;
                String valorStr = valorObj.toString();
                if (valorStr.equals("Sem conta")) return 0.0;
                try {
                    String valorLimpo = valorStr.replace("R$", "")
                                               .replaceAll("\\.", "")
                                               .replace(",", ".")
                                               .trim();
                    return Double.parseDouble(valorLimpo);
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            }
        });
        
        // Coluna 6: Tipo Conta
        sorter.setComparator(6, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                return s1.compareTo(s2);
            }
        });        
    }

    // MÉTODOS PARA ABRIR TELAS

    private void abrirTelaCadastroInclusao() {
        TelaCadastroCliente telaCadastro = new TelaCadastroCliente(this, null); // TelaManterClientes é a tela pai, passa cliente como null para inclusão
        telaCadastro.setLocationRelativeTo(this);
        telaCadastro.setVisible(true);
        atualizarTabela();
    }

    private void abrirTelaCadastroAtualizacao(int indiceModelo) {
        Cliente clienteParaAtualizar = tableModel.getCliente(indiceModelo);
        if (clienteParaAtualizar == null) {
            JOptionPane.showMessageDialog(this, "Erro: Cliente não encontrado.");
            return;
        }

        TelaCadastroCliente telaCadastro = new TelaCadastroCliente(this, clienteParaAtualizar);
        telaCadastro.setLocationRelativeTo(this);
        telaCadastro.setVisible(true);
        atualizarTabela();
    }

    private void abrirTelaVincularConta(int indiceModelo) {
        Cliente clienteSelecionado = tableModel.getCliente(indiceModelo);
        if (clienteSelecionado == null) {
            JOptionPane.showMessageDialog(this, "Erro: Cliente não encontrado.");
            return;
        }

        TelaVincularConta telaVincular = new TelaVincularConta(this, clienteSelecionado);
        telaVincular.setLocationRelativeTo(this);
        telaVincular.setVisible(true);
        atualizarTabela();
    }

    private void abrirTelaOperacoesConta(int indiceModelo) {
        Cliente clienteSelecionado = tableModel.getCliente(indiceModelo);
        if (clienteSelecionado == null) {
            JOptionPane.showMessageDialog(this, "Erro: Cliente não encontrado.");
            return;
        }

        Conta conta = clienteSelecionado.getConta();

        if (conta == null) {
            JOptionPane.showMessageDialog(this,
                    "O cliente " + clienteSelecionado.getNome() + " não possui conta vinculada. Use o botão 'Vincular'.",
                    "Conta inexistente",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        TelaOperacoesConta telaOperacoes = new TelaOperacoesConta(this, conta);
        telaOperacoes.setLocationRelativeTo(this);
        telaOperacoes.setVisible(true);
        atualizarTabela();
    }

    // EXCLUIR CLIENTE

    private void excluirCliente() {
        int linhaView = tabelaClientes.getSelectedRow();
        if (linhaView == -1) {
            JOptionPane.showMessageDialog(this,
                    "Selecione um cliente para excluir.",
                    "Nenhum cliente selecionado",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int linhaModelo = tabelaClientes.convertRowIndexToModel(linhaView);
        Cliente clienteParaExcluir = tableModel.getCliente(linhaModelo);

        int resposta = JOptionPane.showConfirmDialog(
                this,
                "Deseja realmente excluir o cliente " + clienteParaExcluir.getNome() +
                        "?\nTodas as contas dele serão removidas.",
                "Confirmar Exclusão",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (resposta == JOptionPane.YES_OPTION) {
            RepositorioDados.getInstance().removerCliente(clienteParaExcluir);
            atualizarTabela();
            JOptionPane.showMessageDialog(this, "Cliente excluído com sucesso.");
        }
    }

    // ATUALIZAR TABELA

    public void atualizarTabela() {
        List<Cliente> clientes = RepositorioDados.getInstance().getListaClientes();
        tableModel.setClientes(clientes);
        System.out.println("Tabela atualizada com " + clientes.size() + " clientes");
    }
}
