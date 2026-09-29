package com.belval.academia;

import com.belval.academia.util.Cpf;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Validação de CPF: formato, dígitos verificadores e formatação. */
class CpfTest {

    @Test
    void aceitaCpfValidoComESemMascara() {
        assertThat(Cpf.valido("529.982.247-25")).isTrue();
        assertThat(Cpf.valido("52998224725")).isTrue();
        assertThat(Cpf.valido("111.444.777-35")).isTrue();
    }

    @Test
    void rejeitaCpfComDigitoVerificadorErrado() {
        assertThat(Cpf.valido("12345678900")).isFalse();
        assertThat(Cpf.valido("529.982.247-26")).isFalse();
    }

    @Test
    void rejeitaCpfComTamanhoInvalidoOuSequenciaRepetida() {
        assertThat(Cpf.valido("123")).isFalse();
        assertThat(Cpf.valido("1234567890123")).isFalse();
        assertThat(Cpf.valido("11111111111")).isFalse();
        assertThat(Cpf.valido("00000000000")).isFalse();
        assertThat(Cpf.valido(null)).isFalse();
        assertThat(Cpf.valido("   ")).isFalse();
    }

    @Test
    void normalizaEPermiteComparacaoIndependenteDaMascara() {
        assertThat(Cpf.somenteDigitos("529.982.247-25")).isEqualTo("52998224725");
        assertThat(Cpf.somenteDigitos(" 529 982 247 25 ")).isEqualTo("52998224725");
        assertThat(Cpf.somenteDigitos("sem numeros")).isNull();
    }

    @Test
    void formataParaExibicaoSemInventarDados() {
        assertThat(Cpf.formatar("52998224725")).isEqualTo("529.982.247-25");
        assertThat(Cpf.formatar("123")).isEqualTo("123");
    }
}
