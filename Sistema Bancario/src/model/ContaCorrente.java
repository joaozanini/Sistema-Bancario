package model;

public class ContaCorrente extends Conta {

    private double limite;

    public ContaCorrente(Cliente dono, int numero, double depositoInicial, double limite) {
        super(dono, numero, depositoInicial);

        if (limite < 0) {
            throw new IllegalArgumentException("O limite não pode ser negativo.");
        }

        this.limite = limite;
    }

    @Override
    public boolean saca(double valor) {
        if (valor <= 0) {
            throw new IllegalArgumentException("O valor do saque deve ser positivo.");
        }

        if (saldo - valor < -limite) {
            throw new IllegalStateException(
                "Saque excede o limite disponível. Limite permitido: R$ " + limite
            );
        }

        saldo -= valor;
        return true;
    }

    @Override
    public void remunera() {
        if (saldo <= 0) {
            throw new IllegalStateException("Saldo negativo.");
        }
        // Conta Corrente recebe 1% se estiver com saldo positivo
        saldo += saldo * 0.01;
    }

    // Factory method para reconstruir conta carregada do banco de dados
    public static ContaCorrente fromDB(Cliente dono, int numero, double saldo, double limite) {
        ContaCorrente cc = new ContaCorrente(dono, numero, 0.0, limite);
        cc.saldo = saldo; // restaura saldo real (pode ser negativo por uso do limite)
        return cc;
    }

    public double getLimite() {
        return limite;
    }

    public void setLimite(double limite) {

        if (limite < 0) {
            throw new IllegalArgumentException("O limite não pode ser negativo.");
        }

        this.limite = limite;
    }
}
