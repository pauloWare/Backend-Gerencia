package com.belval.academia.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data // Use o Lombok para não precisar escrever getters/setters
// Telefone: índice único no banco criado por V5__unicidade_telefone.sql /
// schema.sql (filtrado WHERE telefone IS NOT NULL — não declarado aqui porque
// o JPA não suporta índice filtrado; ver comentário completo em Aluno).
@Table(indexes = @Index(name = "UX_usuario_email", columnList = "email", unique = true))
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String nome;
    private String email;
    private String senha;
    private String dataNascimento;
    private String telefone;
    private String sexo;
    
    @Column(columnDefinition = "TEXT") // TEXT é mais universal que VARCHAR(MAX) em alguns bancos
    private String fotoPerfilBase64;
    
    private String cargo; // ADMIN, RECEPCIONISTA, FINANCEIRO, TECNICO

    private String status = "user"; // "user" ou "admin"
}