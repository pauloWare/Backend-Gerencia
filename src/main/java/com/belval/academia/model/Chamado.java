package com.belval.academia.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Entity
@Data
public class Chamado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "equipamento_id")
    private Equipamento equipamento;

    @Column(columnDefinition = "TEXT")
    private String problema;

    @Column(columnDefinition = "TEXT")
    private String fotoBase64;

    private LocalDate data;
    private LocalTime hora;
    // BAIXA, MEDIA, ALTA, CRITICA
    private String prioridade;
    private String responsavel;

    // ABERTO, EM_ANDAMENTO, FINALIZADO
    private String status = "ABERTO";

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    // CORRETIVA (padrão). A manutenção preventiva é registrada em ManutencaoPreventiva.
    private String tipo;

    // SLA: prazo esperado, em dias, para resolução a partir da abertura
    private Integer slaDias;

    // Data efetiva de resolução (usada no cálculo do SLA)
    private LocalDate dataConclusao;

    /**
     * Tempo total de atendimento (dias) entre a abertura e a conclusão.
     * Campo computado, sem persistência. Retorna null quando não é possível calcular.
     */
    public Long getTempoAtendimentoDias() {
        if (data == null || dataConclusao == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(data, dataConclusao);
    }

    public String getTipo() {
        return tipo != null ? tipo : "CORRETIVA";
    }

    /**
     * Prazo calculado = data de abertura + slaDias.
     */
    public LocalDate getPrazo() {
        if (data == null || slaDias == null) {
            return null;
        }
        return data.plusDays(slaDias);
    }

    /**
     * Situação do SLA calculada a partir das datas reais.
     * Valores: DENTRO_PRAZO, PROXIMO_VENCIMENTO, SLA_VENCIDO,
     * CONCLUIDO_DENTRO_SLA, CONCLUIDO_APOS_SLA, CONCLUIDO (sem SLA) ou SEM_SLA.
     */
    public String getSlaStatus() {
        LocalDate prazo = getPrazo();
        if (dataConclusao != null) {
            if (prazo == null) {
                return "CONCLUIDO";
            }
            return dataConclusao.isAfter(prazo) ? "CONCLUIDO_APOS_SLA" : "CONCLUIDO_DENTRO_SLA";
        }
        if (prazo == null) {
            return "SEM_SLA";
        }
        LocalDate hoje = LocalDate.now();
        if (hoje.isAfter(prazo)) {
            return "SLA_VENCIDO";
        }
        if (!hoje.plusDays(1).isBefore(prazo)) {
            return "PROXIMO_VENCIMENTO";
        }
        return "DENTRO_PRAZO";
    }
}
