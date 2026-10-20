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

    /**
     * Telefone: mantém apenas os dígitos (remove espaços, parênteses, traços,
     * ponto, barra e {@code +}). Quando o valor vem com o código do país 55 e
     * sobra um número nacional válido (10 ou 11 dígitos), o prefixo é
     * removido — assim {@code "+55 (11) 98765-4321"} e {@code "11987654321"}
     * são o MESMO telefone para comparação e gravação.
     *
     * <p>Retorna {@code null} quando não há dígitos (evita gravar "" — o banco
     * aceita vários {@code NULL} no índice único, mas não vários vazios).</p>
     *
     * <p>É a MESMA normalização usada pela migração
     * {@code V5__unicidade_telefone.sql} antes de criar os índices únicos.</p>
     */
    public static String telefone(String valor) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            return null;
        }
        // 55 + DDD(2) + 8/9 dígitos = 12/13 dígitos no total; um número
        // nacional nunca tem esse comprimento, então o prefixo é do país.
        if (digitos.length() >= 12 && digitos.length() <= 13 && digitos.startsWith("55")) {
            String nacional = digitos.substring(2);
            if (nacional.length() == 10 || nacional.length() == 11) {
                return nacional;
            }
        }
        return digitos;
    }
}
