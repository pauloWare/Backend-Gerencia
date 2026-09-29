package com.belval.academia.repository;

import com.belval.academia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
