package com.belval.academia.dto;

import com.belval.academia.model.Funcionario;
import com.belval.academia.model.Usuario;
import com.belval.academia.util.Cpf;
import lombok.Data;

import java.time.LocalDate;

/**
 * Funcionário devolvido pela API.
 *
 * <p>Mantém exatamente os campos já consumidos pelo frontend (inclusive os
 * dados da conta de acesso) e <b>não expõe senha nem hash</b> em nenhuma
 * hipótese: a senha da conta vinculada nunca é copiada para este DTO.</p>
 */
@Data
public class FuncionarioResponseDTO {

    private Long id;
    private String nome;
    private String cpf;
    private LocalDate dataNascimento;
    private String telefone;
    private String email;
    private String cargo;
    private Double salario;
    private LocalDate dataAdmissao;
    private String situacao;

    private String cep;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String estado;

    // ===== Conta de acesso (sem senha/hash) =====
    private Long usuarioId;
    private String emailAcesso;
    private String cargoAcesso;
    /** "ATIVA" quando existe conta de acesso vinculada. */
    private String situacaoAcesso;

    public FuncionarioResponseDTO() {
    }

    public FuncionarioResponseDTO(Funcionario f, Usuario conta) {
        this.id = f.getId();
        this.nome = f.getNome();
        this.cpf = f.getCpf();
        this.dataNascimento = f.getDataNascimento();
        this.telefone = f.getTelefone();
        this.email = f.getEmail();
        this.cargo = f.getCargo();
        this.salario = f.getSalario();
        this.dataAdmissao = f.getDataAdmissao();
        this.situacao = f.getSituacao();
        this.cep = f.getCep();
        this.logradouro = f.getLogradouro();
        this.numero = f.getNumero();
        this.complemento = f.getComplemento();
        this.bairro = f.getBairro();
        this.cidade = f.getCidade();
        this.estado = f.getEstado();

        if (conta != null) {
            this.usuarioId = conta.getId();
            this.emailAcesso = conta.getEmail();
            this.cargoAcesso = conta.getCargo();
            this.situacaoAcesso = "ATIVA";
        }
    }

    /** CPF formatado para exibição (o banco guarda apenas dígitos). */
    public String getCpfFormatado() {
        return Cpf.formatar(cpf);
    }
}
