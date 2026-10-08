package com.belval.academia.dto;

import lombok.Data;

/**
 * Resposta do login unificado ({@code POST /api/auth/login}).
 *
 * <p>Uma única porta de entrada para toda a academia: o backend confere o e-mail
 * e a senha, identifica se a conta é da equipe ({@code usuario}) ou de um aluno
 * ({@code aluno}) e devolve o token assinado já com o cargo/perfil. O aplicativo
 * apenas direciona a pessoa para a área correspondente — ele nunca decide nem
 * informa qual é o perfil do usuário.</p>
 *
 * <p>Para o aluno, os dados vêm em {@link AlunoSessaoDTO} (id, nome, plano e
 * situação) mais o e-mail usado no login; para a equipe, em
 * {@link UsuarioResponseDTO}. Nenhuma senha ou hash trafega na resposta.</p>
 */
@Data
public class LoginUnificadoResponseDTO {

    private String token;

    /**
     * Perfil resolvido pelo backend: {@code ADMIN}, {@code RECEPCIONISTA},
     * {@code FINANCEIRO}, {@code TECNICO} ou {@code ALUNO}.
     */
    private String cargo;

    private UsuarioResponseDTO usuario;
    private AlunoSessaoDTO aluno;
}