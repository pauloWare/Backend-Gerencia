package com.belval.academia;

import com.belval.academia.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ativacao por codigo (parte 2: reuso, expiracao, codigo trocado). */
class AtivacaoContaRegrasTest extends AtivacaoContaTest {

    @Test
    void codigoNaoPodeSerReutilizado() throws Exception {
        Usuario recepcao = criarUsuario(emailUnico("recepcao3"), "SenhaForte123", "RECEPCIONISTA");
        String emailAluno = emailUnico("aluno-reuso");
        String codigo = extrairCodigo(mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(recepcao)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cadastro(emailAluno))))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(post("/api/portal/ativar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "codigo", codigo,
                                "senha", SENHA_NOVA, "confirmarSenha", SENHA_NOVA))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/portal/ativar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "codigo", codigo,
                                "senha", "OutraSenha123", "confirmarSenha", "OutraSenha123"))))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "senha", SENHA_NOVA))))
                .andExpect(status().isOk());
    }

    @Test
    void codigoExpiradoOuInvalidoEhRejeitadoSemVazarDados() throws Exception {
        Usuario recepcao = criarUsuario(emailUnico("recepcao4"), "SenhaForte123", "RECEPCIONISTA");
        String emailAluno = emailUnico("aluno-exp");
        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(recepcao)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cadastro(emailAluno))))
                .andExpect(status().isOk());
        var aluno = alunoRepository.findAll().stream()
                .filter(a -> emailAluno.equalsIgnoreCase(a.getEmail())).findFirst().orElseThrow();
        var ativacao = ativacaoRepository.findByAlunoId(aluno.getId()).orElseThrow();
        ativacao.setExpiraEm(Instant.now().minusSeconds(60));
        ativacaoRepository.save(ativacao);
        mockMvc.perform(post("/api/portal/ativar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "codigo", "XXXXXXXX",
                                "senha", SENHA_NOVA, "confirmarSenha", SENHA_NOVA))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Não foi possível ativar a conta. Confira o e-mail e o código."))
                .andExpect(jsonPath("$.aluno").doesNotExist())
                .andExpect(jsonPath("$.nome").doesNotExist());
        mockMvc.perform(post("/api/portal/ativar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailUnico("fantasma"), "codigo", "YYYYYYYY",
                                "senha", SENHA_NOVA, "confirmarSenha", SENHA_NOVA))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Não foi possível ativar a conta. Confira o e-mail e o código."));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "senha", SENHA_NOVA))))
                .andExpect(status().isUnauthorized());
    }
}
