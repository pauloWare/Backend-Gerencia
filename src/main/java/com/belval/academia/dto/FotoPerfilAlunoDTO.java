package com.belval.academia.dto;

import lombok.Data;

/**
 * Foto de perfil do aluno em Base64 (data URL).
 *
 * <p>Segue o formato já usado no projeto ({@code data:image/jpeg;base64,...}),
 * como em {@code chamado.foto_base64}. O texto vazio remove a foto atual.</p>
 */
@Data
public class FotoPerfilAlunoDTO {

    private String fotoBase64;
}