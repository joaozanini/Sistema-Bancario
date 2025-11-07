package view;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        // Deixa a aparência igual ao sistema operacional (fica bem legal inclusive)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                TelaManterClientes telaPrincipal = new TelaManterClientes();
                telaPrincipal.setLocationRelativeTo(null);
                telaPrincipal.setVisible(true);
            }
        });
    }
}