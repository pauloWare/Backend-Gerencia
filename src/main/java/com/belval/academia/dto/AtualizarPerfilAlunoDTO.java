package com.belval.academia.dto;

import lombok.Data;

/**
 * Campos que o ALUNO pode editar na própria conta.
 *
 * <p>Deliberadamente restrito a cinco campos pessoais: nome, e-mail, telefone,
 * CPF e data de nascimento. Não há (e não pode haver) id, cargo, plano,
 * situação, mensalidades ou qualquer outro campo administrativo — assim, mesmo
 * com uma requisição forjada, o aluno não altera o próprio cargo/permissões nem
 * mexe em outro cadastro. O id do registro alterado NUNCA vem daqui: ele é
 * sempre derivado do token ({@code UsuarioAutenticado.alunoId()}).</p>
 *
 * <p>Regras de envio (o backend valida tudo de novo, independentemente do app):</p>
 * <ul>
 *   <li>{@code cpf}: com ou sem máscara; nulo/vazio mantém o CPF atual;</li>
 *   <li>{@code dataNascimento}: formato ISO {@code AAAA-MM-DD}; nulo/vazio
 *       mantém a data atual; data futura é recusada.</li>
 * </ul>
 */
@Data
public class AtualizarPerfilAlunoDTO {

    private String nome;
    private String email;
    private String telefone;
    /** CPF com ou sem máscara; nulo/vazio = manter o CPF atual. */
    private String cpf;
    /** Data no formato ISO AAAA-MM-DD; nulo/vazio = manter a data atual. */
    private String dataNascimento;
}