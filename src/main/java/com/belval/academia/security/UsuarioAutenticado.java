package com.belval.academia.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Acesso ao usuário autenticado da requisição (id e cargo), sempre derivado do
 * token validado — nunca de dados enviados pelo cliente.
 */
public final class UsuarioAutenticado {

    private UsuarioAutenticado() {
    }

    /** Id do usuário autenticado, ou {@code null} quando não há autenticação. */
    public static Long id() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !autenticacao.isAuthenticated()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(autenticacao.getName()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Id do ALUNO autenticado (portal mobile), derivado APENAS do token
     * ({@code aluno:<id>}). Devolve {@code null} quando a sessão não é de aluno —
     * é isso que impede um aluno de ler os dados de outro informando um id.
     */
    public static Long alunoId() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !autenticacao.isAuthenticated()) {
            return null;
        }
        String identidade = String.valueOf(autenticacao.getName());
        if (!identidade.startsWith(TokenService.PREFIXO_ALUNO)) {
            return null;
        }
        try {
            return Long.valueOf(identidade.substring(TokenService.PREFIXO_ALUNO.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Cargo (perfil) do usuário autenticado, ou {@code null} quando não há. */
    public static String cargo() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !autenticacao.isAuthenticated()) {
            return null;
        }
        return autenticacao.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
    }

    public static boolean ehAdmin() {
        return "ADMIN".equalsIgnoreCase(cargo());
    }
}
