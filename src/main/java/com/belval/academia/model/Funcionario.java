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

    // Dados pessoais / contato.
    // Estas colunas JÁ existem no banco e JÁ são usadas pelo cadastro atual
    // do frontend; aqui apenas passam a ser mapeadas pela entidade.
    @Column(name = "cpf")
    private String cpf;
    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;
    @Column(name = "email")
    private String email;

    // Endereço estruturado (colunas já existentes no banco).
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
    @Column(name = "cidade")
    private String cidade;
    @Column(name = "estado")
    private String estado;

    // Conta de acesso vinculada (FK funcionario.usuario_id -> usuario.id).
    // Mapeada como coluna simples para preservar o formato já consumido pelo
    // frontend (campo "usuarioId"), SEM alterar a regra de criação de conta.
    @Column(name = "usuario_id")
    private Long usuarioId;
}