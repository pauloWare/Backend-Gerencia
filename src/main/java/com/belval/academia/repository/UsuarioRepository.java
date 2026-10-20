package com.belval.academia.repository;

import com.belval.academia.model.Usuario;
import com.belval.academia.util.Normalizacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Objects;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Busca por e-mail sem diferenciar maiúsculas/minúsculas (mesma política
     * usada antes de gravar, garantindo que um e-mail seja único).
     */
    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Usado na edição: o próprio registro pode manter o seu e-mail. */
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /**
     * Existência de telefone considerando apenas os dígitos (reconhece como
     * duplicados valores gravados com máscara, com ou sem código do país 55).
     * O {@code idIgnorado} permite ignorar um registro em edição — além de
     * ignorar a conta vinculada ao próprio funcionário quando ele edita o
     * telefone (a conta espelha o mesmo número por ser a mesma identidade).
     */
    default boolean existeTelefone(String telefone, Long idIgnorado) {
        String normalizado = Normalizacao.telefone(telefone);
        if (normalizado == null) {
            return false;
        }
        return findAll().stream()
                .filter(u -> u.getTelefone() != null)
                .filter(u -> idIgnorado == null || !Objects.equals(u.getId(), idIgnorado))
                .anyMatch(u -> normalizado.equals(Normalizacao.telefone(u.getTelefone())));
    }
}
