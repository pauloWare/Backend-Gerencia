package com.belval.academia.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
public class Despesa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FUNCIONARIOS, ENERGIA, AGUA, ALUGUEL, EQUIPAMENTOS, MANUTENCAO, OUTROS
    private String tipo;
    // FIXO ou VARIAVEL
    private String classificacao;
    private String descricao;
    private Double valor;
    private LocalDate data;
    // Referência opcional à manutenção (corretiva/preventiva) que originou a despesa
    private Long manutencaoId;
}
