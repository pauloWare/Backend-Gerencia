package com.belval.academia.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
public class ManutencaoPreventiva {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Neste modelo o tipo é sempre PREVENTIVA
    private String tipo;

    @ManyToOne
    @JoinColumn(name = "equipamento_id")
    private Equipamento equipamento;

    @Column(columnDefinition = "TEXT")
    private String servico;

    // Periodicidade em dias (ex.: 30)
    private Integer periodicidade;

    private LocalDate dataUltimaManutencao;
    private LocalDate proximaManutencao;

    private String responsavel;

    // AGENDADA, EM_ANDAMENTO, CONCLUIDA
    private String status = "AGENDADA";

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    public String getTipo() {
        return tipo != null ? tipo : "PREVENTIVA";
    }

    /**
     * Status exibido: se a próxima manutenção já venceu e o serviço não foi
     * concluído, o sistema apresenta a manutenção como ATRASADA.
     */
    public String getStatusExibicao() {
        if (!"CONCLUIDA".equals(status)
                && proximaManutencao != null
                && proximaManutencao.isBefore(LocalDate.now())) {
            return "ATRASADA";
        }
        return status != null ? status : "AGENDADA";
    }
}
