package com.belval.academia.dto;

import com.belval.academia.model.Aluno;
import lombok.Data;

/**
 * Dados do aluno devolvidos no login do portal mobile.
 *
 * <p>Exposição mínima: apenas o necessário para a interface e para o app mostrar
 * a conta com quem está conectado — nome, e-mail, plano e situação. CPF
 * (completo), endereço, observações, atestado médico e senha NUNCA são enviados
 * ao aplicativo.</p>
 */
@Data
public class AlunoSessaoDTO {
    private Long id;
    private String nome;
    /** E-mail usado no login, para o app identificar a conta logada. */
    private String email;
    private String plano;
    private String situacao;

    public AlunoSessaoDTO() {
    }

    public AlunoSessaoDTO(Aluno aluno) {
        this.id = aluno.getId();
        this.nome = aluno.getNome();
        this.email = aluno.getEmail();
        this.plano = aluno.getPlano();
        this.situacao = aluno.getSituacao();
    }
}