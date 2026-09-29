package com.belval.academia.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
// Trava contra corrida simultânea: o CPF é normalizado (só dígitos) antes de
// gravar e validado na aplicação; o índice impede duplicidade no banco.
// E-mails de contato (aluno.email) podem se repetir legitimamente e por isso
// NÃO têm índice único — só a identidade de login (usuario.email) tem.
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
}