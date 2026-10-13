package com.belval.academia.repository;

import com.belval.academia.model.AtivacaoAluno;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AtivacaoAlunoRepository extends JpaRepository<AtivacaoAluno, Long> {

    Optional<AtivacaoAluno> findByAlunoId(Long alunoId);

    /**
     * Busca com trava pessimista para consumo atômico do código (impede
     * ativações concorrentes duplicadas da mesma conta).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AtivacaoAluno a where a.aluno.id = :alunoId")
    Optional<AtivacaoAluno> buscarComTrava(@Param("alunoId") Long alunoId);

    /**
     * Candidatos ao código de um e-mail (alunos sem senha definida). A
     * comparação do código é feita em memória via BCrypt (hash com salt).
     */
    @Query("select a from AtivacaoAluno a join fetch a.aluno al "
            + "where lower(al.email) = lower(:email) and al.senha is null")
    List<AtivacaoAluno> candidatosPorEmail(@Param("email") String email);
}
