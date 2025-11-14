package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import model.Cliente;
import model.RepositorioDados;

public class TelaCadastroCliente extends JDialog {

    private JTextField txtNome;
    private JTextField txtSobrenome;
    private JTextField txtRg;
    private JTextField txtCpf;
    private JTextField txtEndereco;

    private Cliente clienteParaAtualizar;

    public TelaCadastroCliente(Frame owner, Cliente cliente) {
        super(owner, true); // Modal
        
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
        contentPanel.setLayout(new GridLayout(5, 2, 5, 5)); // 5 linhas, 2 colunas

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
        cancelButton.addActionListener(e -> dispose()); // Fecha a janela
        buttonPane.add(cancelButton);
    }
    
    private void preencherCampos() {
        txtNome.setText(clienteParaAtualizar.getNome());
        txtSobrenome.setText(clienteParaAtualizar.getSobrenome());
        txtRg.setText(clienteParaAtualizar.getRg());
        txtCpf.setText(clienteParaAtualizar.getCpf());
        txtCpf.setEnabled(false); // CPF não deve ser editado
        txtEndereco.setText(clienteParaAtualizar.getEndereco());
    }

    private void salvarCliente() {
        if (txtNome.getText().trim().isEmpty() || txtCpf.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                    "Campos 'Nome' e 'CPF' são obrigatórios.", 
                    "Erro de Validação", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String nome = txtNome.getText();
        String sobrenome = txtSobrenome.getText();
        String rg = txtRg.getText();
        String cpf = txtCpf.getText();
        String endereco = txtEndereco.getText();
        
        if (clienteParaAtualizar == null) {
            if (RepositorioDados.getInstance().buscarClientePorCpf(cpf) != null) {
                 JOptionPane.showMessageDialog(this, 
                        "Já existe um cliente com este CPF.", 
                        "Erro", JOptionPane.ERROR_MESSAGE);
                 return;
            }
            
            Cliente novoCliente = new Cliente(nome, sobrenome, rg, cpf, endereco);
            RepositorioDados.getInstance().adicionarCliente(novoCliente);
            JOptionPane.showMessageDialog(this, "Cliente incluído com sucesso!");

        } else {
            clienteParaAtualizar.setNome(nome);
            clienteParaAtualizar.setSobrenome(sobrenome);
            clienteParaAtualizar.setRg(rg);
            clienteParaAtualizar.setEndereco(endereco);
            JOptionPane.showMessageDialog(this, "Cliente atualizado com sucesso!");
        }
        dispose();
    }
}