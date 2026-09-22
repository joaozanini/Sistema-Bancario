package com.bantads.ms_cliente.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "solicitacao_cadastro")
@Getter
@Setter
public class SolicitacaoCadastro {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nome;
    private String cpf;
    private String email;
    private String telefone;
    private BigDecimal salario;
    
    private String logradouro;
    private String numero;
    private String complemento;
    private String cep;
    private String cidade;
    private String uf;

    private String status = "PENDENTE"; 
    
    @Column(name = "motivo_rejeicao")
    private String motivoRejeicao;
    
    @Column(name = "data_aprovacao_rejeicao")
    private LocalDateTime dataAprovacaoRejeicao;

}