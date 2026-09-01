package com.bantads.ms_auth.dto;

import com.bantads.ms_auth.model.Usuario;

public record LoginResponse(String id, String login) {

    public static LoginResponse de(Usuario usuario) {
        return new LoginResponse(usuario.getId(), usuario.getLogin());
    }
}
