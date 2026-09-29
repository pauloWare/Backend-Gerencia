package com.belval.academia.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Dados enviados pelo cadastro/edição de funcionário.
 *
 * <p>Além dos dados cadastrais, o frontend informa os dados da <b>conta de
 * acesso</b> ({@code emailAcesso} + {@code senha}). A senha nunca é devolvida em
 * nenhuma resposta; quando ela não é enviada, a senha atual é preservada.</p>
 */
@Data
public class FuncionarioRequestDTO {

    // ===== Dados cadastrais =====
    private String nome;
    private String cpf;
    private LocalDate dataNascimento;
    private String telefone;
    private String email;
    private String cargo;
    private Double salario;
    private LocalDate dataAdmissao;
    private String situacao;

    private String cep;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String estado;

    // ===== Conta de acesso =====
    /** Vínculo já existente (enviado pelo cliente apenas para preservação). */
    private Long usuarioId;
    /** E-mail usado no login (identidade única). */
    private String emailAcesso;
    /** Senha inicial, ou nova senha. {@code null}/vazio = manter a atual. */
    private String senha;
    /** Conferência da senha (o backend revalida). */
    private String confirmarSenha;
}
