package com.belval.academia;

import com.belval.academia.model.Aluno;
import com.belval.academia.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ativacao por codigo (parte 3: troca, concorrencia, permissao, legado). */
class AtivacaoContaSegurancaTest extends AtivacaoContaTest {

    @Test
    void emailDeUmAlunoComCodigoDeOutroNaoAtiva() throws Exception {
        Usuario recepcao = criarUsuario(emailUnico("recepcao5"), "SenhaForte123", "RECEPCIONISTA");
        String emailA = emailUnico("aluno-a");
        String emailB = emailUnico("aluno-b");
        String codigoA = extrairCodigo(mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(recepcao)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cadastro(emailA))))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(recepcao)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cadastro(emailB))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/portal/ativar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailB, "codigo", codigoA,
                                "senha", SENHA_NOVA, "confirmarSenha", SENHA_NOVA))))
                .andExpect(status().isBadRequest());
        for (String email : new String[]{emailA, emailB}) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", email, "senha", SENHA_NOVA))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void funcionarioNaoAtivaContaAlheiaViaFluxoPublico() throws Exception {
        Usuario recepcao = criarUsuario(emailUnico("recepcao7"), "SenhaForte123", "RECEPCIONISTA");
        String emailAluno = emailUnico("aluno-func");
        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(recepcao)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cadastro(emailAluno))))
                .andExpect(status().isOk());
        var aluno = alunoRepository.findAll().stream()
                .filter(a -> emailAluno.equalsIgnoreCase(a.getEmail())).findFirst().orElseThrow();
        // Token de funcionário com código errado: resposta genérica, sem vazar.
        mockMvc.perform(post("/api/portal/ativar-conta")
                        .header("Authorization", bearer(tokenDe(recepcao)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "codigo", "ERRADO99",
                                "senha", SENHA_NOVA, "confirmarSenha", SENHA_NOVA))))
                .andExpect(status().isBadRequest());
        // Aluno não redefine a senha de outro aluno pela rota autenticada.
        Aluno outro = new Aluno();
        outro.setNome("Outro Aluno");
        outro.setCpf(cpfValidoAleatorio());
        outro.setDataNascimento(LocalDate.of(1991, 2, 2));
        outro.setEmail(emailUnico("outro"));
        outro.setSenha(passwordEncoder.encode("OutraSenha123"));
        outro = alunoRepository.save(outro);
        String tokenOutro = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", outro.getEmail(), "senha", "OutraSenha123"))))
                        .andReturn().getResponse().getContentAsString()).get("token").asText();
        mockMvc.perform(put("/api/aluno/" + aluno.getId() + "/senha-acesso")
                        .header("Authorization", bearer(tokenOutro))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("senha", "Invadida123"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void contaJaComSenhaContinuaFuncionandoEGerarCodigoAvisa() throws Exception {
        Usuario admin = criarUsuario(emailUnico("admin-ativ"), "SenhaForte123", "ADMIN");
        Aluno aluno = new Aluno();
        aluno.setNome("Aluno Antigo");
        aluno.setCpf(cpfValidoAleatorio());
        aluno.setDataNascimento(LocalDate.of(1985, 5, 5));
        aluno.setEmail(emailUnico("antigo"));
        aluno.setPlano("Mensal");
        aluno.setSituacao("ATIVO");
        aluno.setSenha(passwordEncoder.encode("AntigaSenha123"));
        aluno = alunoRepository.save(aluno);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", aluno.getEmail(), "senha", "AntigaSenha123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargo").value("ALUNO"));
        mockMvc.perform(post("/api/aluno/" + aluno.getId() + "/codigo-ativacao")
                        .header("Authorization", bearer(tokenDe(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contaJaAtiva").value(true))
                .andExpect(jsonPath("$.codigoAtivacao").doesNotExist());
        assertThat(ativacaoRepository.findByAlunoId(aluno.getId())).isEmpty();
    }
}
