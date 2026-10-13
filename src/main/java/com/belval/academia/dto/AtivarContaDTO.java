package com.belval.academia.dto;

import lombok.Data;

/**
 * Ativação da conta mobile pelo próprio aluno (fluxo "Ativar minha conta").
 *
 * <p>O aluno informa o e-mail cadastrado pela recepção, o código de ativação
 * recebido presencialmente e a senha que ele mesmo escolhe (com confirmação).
 * CPF e data de nascimento NÃO são aceitos como credencial.</p>
 */
@Data
public class AtivarContaDTO {

    private String email;
    private String codigo;
    private String senha;
    private String confirmarSenha;
}
