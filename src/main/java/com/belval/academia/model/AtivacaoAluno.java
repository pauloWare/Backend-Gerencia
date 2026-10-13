package com.belval.academia.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * Código de ativação do acesso mobile do aluno.
 *
 * <p>Gerado pela recepção no cadastro (uso único, temporário, vinculado ao
 * aluno). O código em texto puro NUNCA é persistido: aqui fica apenas o hash
 * BCrypt, comparado com {@code PasswordEncoder.matches}.</p>
 */
@Entity
@Data
@Table(name = "ativacao_aluno",
        indexes = @Index(name = "UX_ativacao_aluno_aluno", columnList = "aluno_id", unique = true))
public class AtivacaoAluno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Aluno dono do código (um código pendente por aluno). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    /** Hash BCrypt do código (nunca o código em texto puro). */
    @Column(name = "codigo_hash", nullable = false, length = 255)
    private String codigoHash;

    /** Expiração do código. */
    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    /** Tentativas de validação já consumidas (limite anti-força-bruta). */
    @Column(name = "tentativas", nullable = false)
    private int tentativas = 0;

    /** Quando o código foi consumido (null = ainda pendente). */
    @Column(name = "consumido_em")
    private Instant consumidoEm;

    /**
     * Versão otimista: garante consumo atômico mesmo quando duas requisições
     * leem o registro antes de qualquer commit (a perdedora recebe
     * OptimisticLockException e responde erro genérico, sem ativar duas vezes
     * nem gravar senha inconsistente — tudo está na mesma transação).
     */
    @Version
    @Column(name = "versao", nullable = false)
    private Long versao = 0L;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    /** Verdadeiro quando ainda pode ser usado (não consumido e não expirado). */
    public boolean isPendente(Instant agora) {
        return consumidoEm == null && expiraEm != null && agora.isBefore(expiraEm);
    }
}
