package com.belval.academia.repository;

import com.belval.academia.model.Frequencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;

public interface FrequenciaRepository extends JpaRepository<Frequencia, Long> {
    List<Frequencia> findByAlunoId(Long alunoId);

    // Regra de negócio do check-in: um aluno só pode ter uma presença por dia.
    boolean existsByAlunoIdAndData(Long alunoId, LocalDate data);

    // Data/hora oficiais vindas do próprio banco (SQL Server ou PostgreSQL). É a fonte de horário
    // do check-in, evitando depender do relógio do celular do aluno.
    // Retorna Object porque cada banco mapeia CURRENT_TIMESTAMP para um tipo Java
    // diferente (SQL Server -> Timestamp, H2 -> OffsetDateTime). A conversão é feita
    // em dataHoraServidor() no controller.
    @Query(value = "SELECT CURRENT_TIMESTAMP", nativeQuery = true)
    Object dataHoraServidor();
}
