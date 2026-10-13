package com.belval.academia.dto;

import com.belval.academia.model.Aluno;
import lombok.Data;

import java.time.LocalDate;

/**
 * Perfil do aluno devolvido ao aplicativo mobile.
 *
 * <p>Reúne o que a tela de Perfil exibe: dados pessoais (nome, CPF mascarado,
 * nascimento e sexo — somente leitura), contato editável, endereço estruturado
 * editável, contato de emergência editável, plano e situação (somente leitura)
 * e a foto em Base64. Observações, atestado e a senha NUNCA são enviados.</p>
 */
@Data
public class AlunoPerfilDTO {

    private Long id;
    private String nome;
    private String email;
    private String telefone;
    /** CPF sempre mascarado pelo backend. */
    private String cpfMascarado;
    private LocalDate dataNascimento;
    private String sexo;
    private String plano;
    private String situacao;
    /** Foto em Base64 (data URL) ou {@code null} quando o aluno não tem foto. */
    private String fotoPerfilBase64;

    // Endereço estruturado (editável pelo aluno).
    private String cep;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;

    // Contato de emergência (editável pelo aluno).
    private String contatoEmergenciaNome;
    private String contatoEmergenciaTelefone;

    public AlunoPerfilDTO() {
    }

    public AlunoPerfilDTO(Aluno aluno) {
        this.id = aluno.getId();
        this.nome = aluno.getNome();
        this.email = aluno.getEmail();
        this.telefone = aluno.getTelefone();
        this.cpfMascarado = mascararCpf(aluno.getCpf());
        this.dataNascimento = aluno.getDataNascimento();
        this.sexo = aluno.getSexo();
        this.plano = aluno.getPlano();
        this.situacao = aluno.getSituacao();
        this.fotoPerfilBase64 = aluno.getFotoPerfilBase64();
        this.cep = aluno.getCep();
        this.logradouro = aluno.getLogradouro();
        this.numero = aluno.getNumero();
        this.complemento = aluno.getComplemento();
        this.bairro = aluno.getBairro();
        this.contatoEmergenciaNome = aluno.getContatoEmergenciaNome();
        this.contatoEmergenciaTelefone = aluno.getContatoEmergenciaTelefone();
    }

    /**
     * Mantém apenas os dois últimos dígitos do CPF (o app repete a formatação
     * 000.***.***-00). Devolve {@code null} quando não há CPF cadastrado.
     */
    static String mascararCpf(String cpf) {
        if (cpf == null) {
            return null;
        }
        String digitos = cpf.replaceAll("\\D", "");
        if (digitos.length() != 11) {
            return null;
        }
        return "***.***-" + digitos.substring(9);
    }
}