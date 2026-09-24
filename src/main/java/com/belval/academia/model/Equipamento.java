package com.belval.academia.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Equipamento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String marca;
    private String localizacao;

    // ATIVO, MANUTENCAO, INATIVO
    private String situacao = "ATIVO";
}
