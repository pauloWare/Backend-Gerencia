package com.belval.academia.util;

import java.util.Locale;

/**
 * Normalização de dados cadastrais usada antes de gravar/comparar valores.
 *
 * <p>Centraliza as regras para que cadastro e edição usem exatamente os mesmos
 * critérios (evita duplicidade "invisível", como "Paulo@X.com" x "paulo@x.com").</p>
 */
public final class Normalizacao {

    private Normalizacao() {
    }

    /**
     * E-mail: remove espaços nas pontas e converte para minúsculas.
     * Retorna {@code null} quando o valor é nulo ou vazio (evita gravar "").
     */
    public static String email(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim().toLowerCase(Locale.ROOT);
        return normalizado.isEmpty() ? null : normalizado;
    }

    /**
     * CPF: mantém apenas os dígitos (remove pontos, traços e espaços).
     * Retorna {@code null} quando não há dígitos.
     */
    public static String somenteDigitos(String valor) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    /**
     * Texto simples: remove espaços nas pontas. Retorna {@code null} quando fica vazio.
     */
    public static String texto(String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }
}
