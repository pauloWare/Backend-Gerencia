package com.belval.academia.util;

/**
 * Validação de CPF (formato + dígitos verificadores).
 *
 * <p>A mesma regra já é usada no frontend (Funcionários/Alunos); aqui ela é
 * reaplicada no backend, que é a última linha de defesa.</p>
 */
public final class Cpf {

    private Cpf() {
    }

    /** Mantém apenas os dígitos do valor informado. */
    public static String somenteDigitos(String valor) {
        return Normalizacao.somenteDigitos(valor);
    }

    /**
     * Valida um CPF com 11 dígitos (aceita com ou sem máscara).
     */
    public static boolean valido(String valor) {
        String cpf = somenteDigitos(valor);
        if (cpf == null || cpf.length() != 11) {
            return false;
        }
        // Rejeita sequências repetidas (000.000.000-00, 111.111.111-11, ...).
        if (cpf.chars().distinct().count() == 1) {
            return false;
        }
        return digitoConfere(cpf, 9) && digitoConfere(cpf, 10);
    }

    /** Formata um CPF de 11 dígitos para 000.000.000-00 (uso apenas em exibição). */
    public static String formatar(String valor) {
        String cpf = somenteDigitos(valor);
        if (cpf == null || cpf.length() != 11) {
            return valor == null ? null : valor.trim();
        }
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "."
                + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    /**
     * Calcula se o dígito verificador da posição {@code posicao} confere.
     * A posição 9 refere-se ao 1º dígito verificador e a 10 ao 2º.
     */
    private static boolean digitoConfere(String cpf, int posicao) {
        int soma = 0;
        for (int i = 0; i < posicao; i++) {
            soma += (cpf.charAt(i) - '0') * ((posicao + 1) - i);
        }
        int resto = soma % 11;
        int digito = resto < 2 ? 0 : 11 - resto;
        return digito == (cpf.charAt(posicao) - '0');
    }
}
