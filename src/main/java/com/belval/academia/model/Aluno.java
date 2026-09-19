package com.belval.academia.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
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
    private String endereco;
    private String contatoEmergencia;
    private LocalDate dataMatricula;
    private String plano;

    // ATIVO, INATIVO, SUSPENSO, CANCELADO
    private String situacao = "ATIVO";

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(columnDefinition = "TEXT")
    private String atestadoMedicoBase64;
}