package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import model.Cliente;
import controller.ClienteController;

public class TelaCadastroCliente extends JDialog {

    private JTextField txtNome;
    private JTextField txtSobrenome;
    private JTextField txtRg;
    private JTextField txtCpf;
    private JTextField txtEndereco;

    private Cliente clienteParaAtualizar;
    private final ClienteController clienteController = new ClienteController();

    public TelaCadastroCliente(Frame owner, Cliente cliente) {
        super(owner, true); 
        
        this.clienteParaAtualizar = cliente;

        if (cliente == null) {
            setTitle("Incluir Novo Cliente");
        } else {
            setTitle("Atualizar Cliente");
        }

        setBounds(150, 150, 450, 300);
        getContentPane().setLayout(new BorderLayout());

        JPanel contentPanel = new JPanel();
        contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        getContentPane().add(contentPanel, BorderLayout.CENTER);
        contentPanel.setLayout(new GridLayout(5, 2, 5, 5));

        contentPanel.add(new JLabel("Nome:"));
        txtNome = new JTextField();
        contentPanel.add(txtNome);

        contentPanel.add(new JLabel("Sobrenome:"));
        txtSobrenome = new JTextField();
        contentPanel.add(txtSobrenome);

        contentPanel.add(new JLabel("RG:"));
        txtRg = new JTextField();
        contentPanel.add(txtRg);

        contentPanel.add(new JLabel("CPF:"));
        txtCpf = new JTextField();
        contentPanel.add(txtCpf);

        contentPanel.add(new JLabel("Endereço:"));
        txtEndereco = new JTextField();
        contentPanel.add(txtEndereco);

        if (cliente != null) {
            preencherCampos();
        }

        JPanel buttonPane = new JPanel();
        buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
        getContentPane().add(buttonPane, BorderLayout.SOUTH);

        JButton okButton = new JButton("Salvar");
        okButton.addActionListener(e -> salvarCliente());
        buttonPane.add(okButton);

        JButton cancelButton = new JButton("Cancelar");
        cancelButton.addActionListener(e -> dispose());
        buttonPane.add(cancelButton);
    }

    private void preencherCampos() {
        txtNome.setText(clienteParaAtualizar.getNome());
        txtSobrenome.setText(clienteParaAtualizar.getSobrenome());
        txtRg.setText(clienteParaAtualizar.getRg());
        txtCpf.setText(clienteParaAtualizar.getCpf());
        txtCpf.setEnabled(false); 
        txtEndereco.setText(clienteParaAtualizar.getEndereco());
    }

    private void salvarCliente() {

    // Validação simples obrigatória
    if (txtNome.getText().trim().isEmpty() ||
        txtCpf.getText().trim().isEmpty() ||
        txtRg.getText().trim().isEmpty()) 
    {
        JOptionPane.showMessageDialog(this,
                "Nome, CPF e RG são obrigatórios.",
                "Erro de Validação",
                JOptionPane.ERROR_MESSAGE);
        return;
    }

    String nome = txtNome.getText().trim();
    String sobrenome = txtSobrenome.getText().trim();
    String rgRaw = txtRg.getText().trim();
    String cpfRaw = txtCpf.getText().trim();
    String endereco = txtEndereco.getText().trim();

    // normaliza: tira pontos, traços, espaços
    String cpfDigits = cpfRaw.replaceAll("\\D", "");
    String rgDigits = rgRaw.replaceAll("\\D", "");

    try {
        // 1) valida formato e algoritmo usando os validadores do modelo
        Cliente.validarRg(rgDigits);
        Cliente.validarCpf(cpfDigits);

        // 2) verifica unicidade no banco de dados
        if (clienteParaAtualizar == null) {
            if (clienteController.buscarPorCpf(cpfDigits) != null) {
                JOptionPane.showMessageDialog(this,
                        "Já existe um cliente com este CPF.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (clienteController.buscarPorRg(rgDigits) != null) {
                JOptionPane.showMessageDialog(this,
                        "Já existe um cliente com este RG.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Cliente novo = new Cliente(nome, sobrenome, rgDigits, cpfDigits, endereco);
            clienteController.incluir(novo);
            JOptionPane.showMessageDialog(this, "Cliente incluído com sucesso!");

        } else {
            // atualização: verificar duplicidade de RG com outro cliente
            Cliente outroRg = clienteController.buscarPorRg(rgDigits);
            if (outroRg != null && !outroRg.getCpf().equals(clienteParaAtualizar.getCpf())) {
                JOptionPane.showMessageDialog(this,
                        "Outro cliente já utiliza este RG.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            clienteParaAtualizar.setNome(nome);
            clienteParaAtualizar.setSobrenome(sobrenome);
            clienteParaAtualizar.setRg(rgDigits);
            clienteParaAtualizar.setEndereco(endereco);
            clienteController.atualizar(clienteParaAtualizar);

            JOptionPane.showMessageDialog(this, "Cliente atualizado com sucesso!");
        }

        dispose();

    } catch (IllegalArgumentException ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro de Formato", JOptionPane.ERROR_MESSAGE);
    } catch (RuntimeException ex) {
        JOptionPane.showMessageDialog(this,
                "Erro ao salvar no banco de dados:\n" + ex.getMessage(),
                "Erro de Banco de Dados", JOptionPane.ERROR_MESSAGE);
    }
    }
}
