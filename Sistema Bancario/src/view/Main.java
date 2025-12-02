package view;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); // pega o estilo nativo do sistema operacional
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                TelaManterClientes telaPrincipal = new TelaManterClientes(); // Cria tela principal
                telaPrincipal.setLocationRelativeTo(null); // Centraliza a janela na tela
                telaPrincipal.setVisible(true); // Exibe a janela para o usuário.
            }
        });
    }
}
