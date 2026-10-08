package com.belval.academia.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
// Trava contra corrida simultânea: o CPF é normalizado (só dígitos) antes de
// gravar e validado na aplicação; o índice impede duplicidade no banco.
// E-mails de contato (aluno.email) podem se repetir legitimamente entre alunos
// (ex.: mesma família) e por isso NÃO têm índice único — só a identidade de
// login da equipe (usuario.email) tem. A unicidade do e-mail de LOGIN do aluno
// é garantida pela aplicação (AlunoAcessoService), que só considera os alunos
// que já possuem senha de acesso definida.
@Table(indexes = @Index(name = "UX_aluno_cpf", columnList = "cpf", unique = true))
public class Aluno {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String cpf;
    private LocalDate dataNascimento;
    private String sexo;
    private String telefone;
    private String email;

    // Endereço legado (texto único) — preservado para não perder cadastros antigos.
    private String endereco;
    // Contato de emergência legado (texto único) — preservado.
    private String contatoEmergencia;

    // Endereço estruturado + contato de emergência estruturado.
    // Estas colunas JÁ existem no banco e JÁ são usadas pelo cadastro atual
    // do frontend; aqui apenas passam a ser mapeadas pela entidade.
    @Column(name = "cep")
    private String cep;
    @Column(name = "logradouro")
    private String logradouro;
    @Column(name = "numero")
    private String numero;
    @Column(name = "complemento")
    private String complemento;
    @Column(name = "bairro")
    private String bairro;
    @Column(name = "contato_emergencia_nome")
    private String contatoEmergenciaNome;
    @Column(name = "contato_emergencia_telefone")
    private String contatoEmergenciaTelefone;

    private LocalDate dataMatricula;
    private String plano;

    // ATIVO, INATIVO, SUSPENSO, CANCELADO
    private String situacao = "ATIVO";

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(columnDefinition = "TEXT")
    private String atestadoMedicoBase64;

    // ======================================================================
    // ACESSO DO ALUNO AO APLICATIVO (login por e-mail e senha)
    // ======================================================================
    // O aluno entrou no sistema como um cadastro próprio (tabela `aluno`) e
    // agora também é uma conta de acesso do app. Guardamos a senha ao lado do
    // cadastro — e NÃO em `usuario` — para não duplicar a pessoa em duas
    // tabelas e não interferir na gestão de contas da equipe (ADMIN,
    // RECEPCIONISTA, FINANCEIRO, TECNICO), que é mantida pelo frontend web.
    //
    // Regras:
    //  - sempre hash BCrypt (nunca texto puro), gerado pelo PasswordEncoder;
    //  - nulo = o aluno ainda não tem senha definida (conta de acesso ainda
    //    não liberada pela recepção); nesse caso o login é recusado com a
    //    mesma mensagem genérica dos demais erros de credencial;
    //  - o hash NUNCA é devolvido em respostas (ver AlunoPerfilDTO e o
    //    @JsonIgnore abaixo, que também impede gravar por JSON).
    @JsonIgnore
    @Column(length = 255)
    private String senha;

    // Foto de perfil em Base64 (data URL), mesma solução já usada em
    // `chamado.foto_base64` e `usuario.foto_perfil_base64`: o dado fica no
    // banco, então sobrevive a reinício do app, do servidor e de novos deploys
    // (um caminho de arquivo temporário do aparelho não sobreviveria).
    @Column(name = "foto_perfil_base64", columnDefinition = "TEXT")
    private String fotoPerfilBase64;

    /** Verdadeiro quando o aluno já possui senha de acesso definida. */
    @JsonIgnore
    public boolean possuiSenhaDeAcesso() {
        return senha != null && !senha.isBlank();
    }
}