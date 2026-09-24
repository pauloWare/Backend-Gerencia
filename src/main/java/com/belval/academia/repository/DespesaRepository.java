package com.belval.academia.repository;

import com.belval.academia.model.Despesa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface DespesaRepository extends JpaRepository<Despesa, Long> {
    List<Despesa> findByDataBetween(LocalDate inicio, LocalDate fim);
}
