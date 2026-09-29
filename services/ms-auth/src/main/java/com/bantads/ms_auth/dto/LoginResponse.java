package com.bantads.ms_auth.dto;

import com.bantads.ms_auth.model.TipoUsuario;
import com.bantads.ms_auth.model.Usuario;

public record LoginResponse(String id, String login, String cpf, TipoUsuario tipo) {

    public static LoginResponse de(Usuario usuario) {
        return new LoginResponse(usuario.getId(), usuario.getLogin(), usuario.getCpf(), usuario.getTipo());
    }
}
