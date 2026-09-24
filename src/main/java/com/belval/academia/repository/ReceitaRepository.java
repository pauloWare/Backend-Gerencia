package com.belval.academia.repository;

import com.belval.academia.model.Receita;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface ReceitaRepository extends JpaRepository<Receita, Long> {
    List<Receita> findByDataBetween(LocalDate inicio, LocalDate fim);

    List<Receita> findByMensalidadeId(Long mensalidadeId);
}
