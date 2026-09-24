package com.belval.academia.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
public class Mensalidade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    private LocalDate dataVencimento;
    private LocalDate dataPagamento;
    private Double valor;

    // Formas fictícias aceitas no TCC: DINHEIRO, CARTAO, PIX
    private String formaPagamento;

    // Usuário logado que registrou o pagamento
    private String usuarioResponsavel;

    // PENDENTE, PAGA, ATRASADA
    private String status = "PENDENTE";
}