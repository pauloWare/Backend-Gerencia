package com.belval.academia.dto;

import lombok.Data;

/**
 * Credenciais de acesso do aluno ao portal mobile (e-mail e senha).
 *
 * <p>A senha é conferida contra o hash BCrypt gravado em {@code Aluno.senha} e
 * NUNCA é derivada do CPF ou da data de nascimento: nenhum dos dois é mais
 * usado como credencial. Nenhum valor é gravado em log.</p>
 */
@Data
public class AlunoLoginRequestDTO {
    private String email;
    private String senha;
}