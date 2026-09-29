package com.belval.academia.exception;

import org.springframework.http.HttpStatus;

/**
 * Erro de regra de negócio já tratado (mensagem segura para o cliente).
 *
 * <p>O {@code campo} é opcional e permite que o frontend destaque o campo com
 * problema (ex.: {@code "cpf"}, {@code "emailAcesso"}).</p>
 */
public class ErroDeRegraDeNegocio extends RuntimeException {

    private final HttpStatus status;
    private final String campo;

    public ErroDeRegraDeNegocio(HttpStatus status, String mensagem) {
        this(status, mensagem, null);
    }

    public ErroDeRegraDeNegocio(HttpStatus status, String mensagem, String campo) {
        super(mensagem);
        this.status = status;
        this.campo = campo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCampo() {
        return campo;
    }
}
