package com.belval.academia.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
public class Funcionario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String cargo;
    private String telefone;
    private Double salario;
    private LocalDate dataAdmissao;

    // ATIVO, INATIVO, AFASTADO
    private String situacao = "ATIVO";
}