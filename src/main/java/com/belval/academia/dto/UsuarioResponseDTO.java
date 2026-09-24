package com.belval.academia.dto;

import com.belval.academia.model.Usuario;
import lombok.Data;

@Data
public class UsuarioResponseDTO {
    private Long id;
    private String nome;
    private String email;
    private String dataNascimento;
    private String telefone;
    private String sexo;
    private String cargo;
    private String status;

    public UsuarioResponseDTO() {
    }

    public UsuarioResponseDTO(Usuario usuario) {
        this.id = usuario.getId();
        this.nome = usuario.getNome();
        this.email = usuario.getEmail();
        this.dataNascimento = usuario.getDataNascimento();
        this.telefone = usuario.getTelefone();
        this.sexo = usuario.getSexo();
        this.cargo = usuario.getCargo();
        this.status = usuario.getStatus();
    }
}
