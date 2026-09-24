package com.belval.academia.repository;

import com.belval.academia.model.Aluno;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AlunoRepository extends JpaRepository<Aluno, Long> {

    Optional<Aluno> findByCpf(String cpf);

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
