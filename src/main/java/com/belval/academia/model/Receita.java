package com.belval.academia.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
public class Receita {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // MENSALIDADE, VENDA_PRODUTOS, OUTROS
    private String tipo;
    private String descricao;
    private Double valor;
    private LocalDate data;

    @ManyToOne
    @JoinColumn(name = "aluno_id", nullable = true)
    private Aluno aluno;

    /**
     * Vínculo 1:1 com a Mensalidade que originou esta Receita (quando tipo = MENSALIDADE).
     * Preenchido no fluxo POST /api/mensalidade/{id}/pagar e usado na edição de uma
     * mensalidade PAGA para atualizar a Receita correspondente sem duplicar registros.
     */
    @Column(name = "mensalidade_id")
    private Long mensalidadeId;
}
