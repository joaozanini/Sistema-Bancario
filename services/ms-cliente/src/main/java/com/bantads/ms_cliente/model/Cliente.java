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
    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(nullable = false)
    private BigDecimal salario;

    @Column(nullable = false, length = 150)
    private String logradouro;

    @Column(nullable = false, length = 10)
    private String numero;

    @Column(length = 100)
    private String complemento;

    @Column(nullable = false, length = 9)
    private String cep;

    @Column(nullable = false, length = 100)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String uf;
}
