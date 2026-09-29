package com.bantads.ms_cliente.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "cliente")
@Getter
@Setter
public class Cliente {

    @Id
    private String cpf;

    private String nome;

    @Column(unique = true)
    private String email;

    private String telefone;
    private BigDecimal salario;

    private String logradouro;
    private String numero;
    private String complemento;
    private String cep;
    private String cidade;
    private String uf;
}
