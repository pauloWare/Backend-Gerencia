package com.belval.academia.repository;

import com.belval.academia.model.Funcionario;
import com.belval.academia.util.Normalizacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Objects;
import java.util.Optional;

public interface FuncionarioRepository extends JpaRepository<Funcionario, Long> {

    Optional<Funcionario> findByCpf(String cpf);

    boolean existsByCpf(String cpf);

    Optional<Funcionario> findByUsuarioId(Long usuarioId);

    /**
     * Existência de CPF considerando apenas os dígitos, para reconhecer como
     * duplicados valores gravados com ou sem máscara (registros legados).
     * O {@code idIgnorado} permite que, na edição, o funcionário mantenha o próprio CPF.
     */
    default boolean existeCpf(String cpf, Long idIgnorado) {
        String digitos = Normalizacao.somenteDigitos(cpf);
        if (digitos == null) {
            return false;
        }
        return findAll().stream()
                .filter(f -> f.getCpf() != null)
                .filter(f -> idIgnorado == null || !Objects.equals(f.getId(), idIgnorado))
                .anyMatch(f -> digitos.equals(Normalizacao.somenteDigitos(f.getCpf())));
    }
}
