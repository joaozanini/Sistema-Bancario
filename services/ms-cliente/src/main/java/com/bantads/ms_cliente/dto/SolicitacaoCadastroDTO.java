package com.bantads.ms_cliente.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// Entrada do autocadastro (R1) e saída das consultas de solicitação (R8): na entrada só chegam os dados
// do cliente; status, motivo, data/hora e _links são preenchidos pelo serviço
@Getter
@Setter
public class SolicitacaoCadastroDTO {
    private String cpf;
    private String nome;
    private String email;
    private String telefone;
    private String salario;
    private EnderecoDTO endereco;

    private String status;
    private String motivo;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dataHoraProcessamento;

    @JsonProperty("_links")
    private Map<String, Map<String, String>> _links = new LinkedHashMap<>();

    public void addLink(String rel, String href) {
        this._links.put(rel, Map.of("href", href));
    }

    @JsonProperty("_links")
    public Map<String, Map<String, String>> get_links() {
        return _links;
    }

    @JsonProperty("_links")
    public void set_links(Map<String, Map<String, String>> _links) {
        this._links = _links;
    }
}
