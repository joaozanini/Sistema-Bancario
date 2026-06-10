package model;

public class ContaInvestimento extends Conta {

    private double montanteMinimo;
    private double depositoMinimo;

    public ContaInvestimento(Cliente dono, int numero, double depositoInicial,
                             double montanteMinimo, double depositoMinimo) {
        super(dono, numero, depositoInicial);
        this.montanteMinimo = montanteMinimo;
        this.depositoMinimo = depositoMinimo;

        if (depositoInicial < montanteMinimo) {
            throw new IllegalArgumentException(
                "Depósito inicial deve ser >= montante mínimo de R$ " + montanteMinimo
            );
        }
    }

    @Override
    public boolean deposita(double valor) {

        if (valor < depositoMinimo) {
            throw new IllegalArgumentException(
                "Depósito mínimo exigido: R$ " + depositoMinimo
            );
        }

        return super.deposita(valor);
    }

    @Override
    public boolean saca(double valor) {

        if (valor <= 0) {
            throw new IllegalArgumentException(
                "Valor de saque deve ser maior que zero."
            );
        }

        if ((saldo - valor) < montanteMinimo) {
            throw new IllegalArgumentException(
                "Saque não permitido. O saldo após o saque deve ser >= R$ " + montanteMinimo
            );
        }

        return super.saca(valor);
    }

    @Override
    public void remunera() {
        if (saldo <= 0) {
            throw new IllegalStateException("Saldo negativo.");
        }
        // Conta Corrente recebe 2% se estiver com saldo positivo
        saldo += saldo * 0.02;
    }

    // Factory method para reconstruir conta carregada do banco de dados
    public static ContaInvestimento fromDB(Cliente dono, int numero, double saldo,
                                           double montanteMinimo, double depositoMinimo) {
        ContaInvestimento ci = new ContaInvestimento(dono, numero, montanteMinimo, montanteMinimo, depositoMinimo);
        ci.saldo = saldo; // restaura saldo real armazenado no banco
        return ci;
    }

    public double getMontanteMinimo() { return montanteMinimo; }
    public void setMontanteMinimo(double montanteMinimo) { this.montanteMinimo = montanteMinimo; }

    public double getDepositoMinimo() { return depositoMinimo; }
    public void setDepositoMinimo(double depositoMinimo) { this.depositoMinimo = depositoMinimo; }
}
