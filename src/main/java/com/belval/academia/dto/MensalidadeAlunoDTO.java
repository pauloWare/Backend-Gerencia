package com.belval.academia.dto;

import com.belval.academia.model.Mensalidade;
import lombok.Data;

import java.time.LocalDate;

/**
 * Mensalidade do próprio aluno no portal mobile.
 *
 * <p>Expõe apenas os campos financeiros necessários à consulta. O objeto
 * {@code aluno} (que no endpoint administrativo carrega dados pessoais e o
 * atestado médico) NÃO é exposto aqui — o aluno já sabe quem é e não precisa
 * receber novamente a própria ficha completa.</p>
 */
@Data
public class MensalidadeAlunoDTO {
    private Long id;
    private LocalDate dataVencimento;
    private LocalDate dataPagamento;
    private Double valor;
    private String status;
    private String formaPagamento;
    /** Rótulo derivado no backend: PAGA, ATRASADA ou PENDENTE. */
    private String situacao;

    public MensalidadeAlunoDTO() {
    }

    public MensalidadeAlunoDTO(Mensalidade mensalidade, String situacao) {
        this.id = mensalidade.getId();
        this.dataVencimento = mensalidade.getDataVencimento();
        this.dataPagamento = mensalidade.getDataPagamento();
        this.valor = mensalidade.getValor();
        this.status = mensalidade.getStatus();
        this.formaPagamento = mensalidade.getFormaPagamento();
        this.situacao = situacao;
    }
}