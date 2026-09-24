package com.belval.academia.repository;

import com.belval.academia.model.Mensalidade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MensalidadeRepository extends JpaRepository<Mensalidade, Long> {
    List<Mensalidade> findByAlunoId(Long alunoId);
}
