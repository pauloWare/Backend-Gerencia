package com.belval.academia.dto;

import lombok.Data;

/**
 * Dados aceitos na criação de uma conta de acesso (rota administrativa).
 *
 * <p>DTO explícito (e não a entidade) para evitar que o cliente envie campos
 * como {@code id} ou {@code status} e ganhe privilégio indevido.</p>
 */
@Data
public class CadastroUsuarioDTO {
    private String nome;
    private String email;
    private String senha;
    private String cargo;
    private String telefone;
    private String sexo;
    private String dataNascimento;
}
