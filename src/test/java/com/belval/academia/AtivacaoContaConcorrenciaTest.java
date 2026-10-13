package com.belval.academia;

import com.belval.academia.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ativacao por codigo (parte 4: concorrencia e regras de senha). */
class AtivacaoContaConcorrenciaTest extends AtivacaoContaTest {

    @Test
    void regrasDeSenhaDoProjetoValemNaAtivacao() throws Exception {
        Usuario recepcao = criarUsuario(emailUnico("recepcao8"), "SenhaForte123", "RECEPCIONISTA");
        String emailAluno = emailUnico("aluno-senha");
        String codigo = extrairCodigo(mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(recepcao)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cadastro(emailAluno))))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(post("/api/portal/ativar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "codigo", codigo,
                                "senha", "123", "confirmarSenha", "123"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/portal/ativar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "codigo", codigo,
                                "senha", SENHA_NOVA, "confirmarSenha", "Diferente123"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/portal/ativar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "codigo", codigo,
                                "senha", SENHA_NOVA, "confirmarSenha", SENHA_NOVA))))
                .andExpect(status().isOk());
    }

    @Test
    void ativacoesConcorrentesConsomemUmaUnicaVez() throws Exception {
        Usuario recepcao = criarUsuario(emailUnico("recepcao6"), "SenhaForte123", "RECEPCIONISTA");
        String emailAluno = emailUnico("aluno-conc");
        String codigo = extrairCodigo(mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(recepcao)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cadastro(emailAluno))))
                .andExpect(status().isOk())
                .andReturn());
        // MockMvc compartilha o contexto transacional do teste: concorrência
        // real aqui serializa no lock pessimista da MESMA transação. A prova
        // atômica (duas transações distintas) vive no serviço com @Version:
        // a segunda ativação encontra o código consumido e falha.
        ativacaoService.ativar(emailAluno, codigo, "Concorrente0x", "Concorrente0x");
        try {
            ativacaoService.ativar(emailAluno, codigo, "Concorrente1x", "Concorrente1x");
            assertThat(false).as("segunda ativação deveria falhar").isTrue();
        } catch (Exception e) {
            assertThat(e).isNotNull();
        }
        // A primeira senha continua valendo (sem ativação inconsistente).
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "senha", "Concorrente0x"))))
                .andExpect(status().isOk());
        // O código foi consumido: nova tentativa é rejeitada sem token/dados.
        mockMvc.perform(post("/api/portal/ativar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "codigo", codigo,
                                "senha", "OutraSenha999", "confirmarSenha", "OutraSenha999"))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.token").doesNotExist());
    }
}
