package com.belval.academia.repository;

import com.belval.academia.model.Aluno;
import com.belval.academia.util.Normalizacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Objects;
import java.util.Optional;

public interface AlunoRepository extends JpaRepository<Aluno, Long> {

    Optional<Aluno> findByCpf(String cpf);

    boolean existsByCpf(String cpf);

    /**
     * Existência de CPF considerando apenas os dígitos, para reconhecer como
     * duplicados valores gravados com ou sem máscara (registros legados).
     * O {@code idIgnorado} permite que, na edição, o aluno mantenha o próprio CPF.
     */
    default boolean existeCpf(String cpf, Long idIgnorado) {
        String digitos = Normalizacao.somenteDigitos(cpf);
        if (digitos == null) {
            return false;
        }
        return findAll().stream()
                .filter(a -> a.getCpf() != null)
                .filter(a -> idIgnorado == null || !Objects.equals(a.getId(), idIgnorado))
                .anyMatch(a -> digitos.equals(Normalizacao.somenteDigitos(a.getCpf())));
    }

    /**
     * Localiza um aluno pelo CPF aceitando o valor com ou sem máscara.
     * Primeiro tenta a busca exata (findByCpf); se não encontrar, compara apenas os
     * dígitos, pois o CPF pode estar gravado formatado (ex.: "123.456.789-01").
     */
    default Optional<Aluno> localizarPorCpf(String cpf) {
        if (cpf == null) return Optional.empty();
        String digitos = cpf.replaceAll("\\D", "");
        if (digitos.isEmpty()) return Optional.empty();
        Optional<Aluno> exato = findByCpf(cpf);
        if (exato.isPresent()) return exato;
        return findAll().stream()
                .filter(a -> a.getCpf() != null && a.getCpf().replaceAll("\\D", "").equals(digitos))
                .findFirst();
    }
}
