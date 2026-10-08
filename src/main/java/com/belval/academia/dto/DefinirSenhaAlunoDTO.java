package com.belval.academia.dto;

import lombok.Data;

/**
 * Senha de acesso que a recepção/administrador define para um aluno.
 *
 * <p>Segue o mesmo padrão de {@link FuncionarioRequestDTO} (senha + confirmação
 * opcional). A senha é gravada somente como hash BCrypt e nunca é devolvida.</p>
 */
@Data
public class DefinirSenhaAlunoDTO {

    private String senha;
    private String confirmarSenha;
}