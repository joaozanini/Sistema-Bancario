package com.bantads.ms_cliente.model;

// Estados da solicitação de autocadastro, com a grafia do contrato da API (R8)
public final class StatusSolicitacao {

    public static final String PENDENTE = "PENDENTE";
    public static final String APROVADA = "APROVADA";
    public static final String NAO_APROVADA = "NAO_APROVADA";

    private StatusSolicitacao() {
    }

    // Aceita também a grafia antiga (APROVADO / NAO_APROVADO): ela ainda pode chegar no filtro da listagem
    // ou numa compensação de SAGA publicada antes da troca. Devolve null para o que não for um estado
    public static String normalizar(String status) {
        if (status == null) {
            return null;
        }
        String valor = status.trim().toUpperCase();
        if (valor.equals(PENDENTE)) {
            return PENDENTE;
        }
        if (valor.equals(APROVADA) || valor.equals("APROVADO")) {
            return APROVADA;
        }
        if (valor.equals(NAO_APROVADA) || valor.equals("NAO_APROVADO")) {
            return NAO_APROVADA;
        }
        return null;
    }
}
