package com.belval.academia;

import com.belval.academia.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tokens: emissão, validação, expiração e permissões por cargo. */
class TokenTest extends TesteBase {

    @Test
    void endpointProtegidoSemTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/aluno"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").isNotEmpty());
    }

    @Test
    void tokenInvalidoRetorna401() throws Exception {
        mockMvc.perform(get("/api/aluno").header("Authorization", "Bearer token.completamente.invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenComAssinaturaDeOutraChaveRetorna401() throws Exception {
        // Token bem formado, porém assinado com outra chave (não pode ser aceito).
        String tokenFalso = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiY2FyZ28iOiJBRE1JTiIsImlzcyI6ImdlcmVuY2lhLWFjYWRlbWlhIiwiZXhwIjo0MTAyNDQ0ODAwfQ."
                + "assinatura-invalida";
        mockMvc.perform(get("/api/aluno").header("Authorization", "Bearer " + tokenFalso))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoRetorna401() throws Exception {
        Usuario u = criarUsuario(emailUnico("expirado"), "SenhaForte123", "ADMIN");

        mockMvc.perform(get("/api/aluno").header("Authorization", bearer(tokenExpiradoDe(u))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenNaQueryStringNaoAutentica() throws Exception {
        Usuario u = criarUsuario(emailUnico("query"), "SenhaForte123", "ADMIN");

        mockMvc.perform(get("/api/aluno").param("token", tokenDe(u)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoAcessaEndpointProtegido() throws Exception {
        Usuario u = criarUsuario(emailUnico("valido"), "SenhaForte123", "ADMIN");

        mockMvc.perform(get("/api/aluno").header("Authorization", bearer(tokenDe(u))))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioAutenticadoSemPermissaoRecebe403() throws Exception {
        Usuario tecnico = criarUsuario(emailUnico("tecnico"), "SenhaForte123", "TECNICO");
        Usuario recepcao = criarUsuario(emailUnico("recepcao"), "SenhaForte123", "RECEPCIONISTA");

        mockMvc.perform(get("/api/financeiro/receitas").header("Authorization", bearer(tokenDe(tecnico))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/funcionario").header("Authorization", bearer(tokenDe(recepcao))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/usuario/1").header("Authorization", bearer(tokenDe(recepcao))))
                .andExpect(status().isForbidden());
    }

    @Test
    void perfisAtuaisMantemSuasPermissoes() throws Exception {
        Usuario admin = criarUsuario(emailUnico("p-admin"), "SenhaForte123", "ADMIN");
        Usuario financeiro = criarUsuario(emailUnico("p-fin"), "SenhaForte123", "FINANCEIRO");
        Usuario tecnico = criarUsuario(emailUnico("p-tec"), "SenhaForte123", "TECNICO");
        Usuario recepcao = criarUsuario(emailUnico("p-rec"), "SenhaForte123", "RECEPCIONISTA");

        // ADMIN: acesso amplo.
        esperar(get("/api/financeiro/receitas"), admin, 200);
        esperar(get("/api/funcionario"), admin, 200);
        esperar(get("/api/dashboard"), admin, 200);
        esperar(get("/api/manutencao/equipamentos"), admin, 200);

        // FINANCEIRO: dashboard, financeiro, mensalidades, relatórios (lê aluno) e funcionários.
        esperar(get("/api/dashboard"), financeiro, 200);
        esperar(get("/api/financeiro/receitas"), financeiro, 200);
        esperar(get("/api/mensalidade"), financeiro, 200);
        esperar(get("/api/aluno"), financeiro, 200);
        esperar(get("/api/funcionario"), financeiro, 200);
        esperar(get("/api/manutencao/chamados"), financeiro, 403);

        // TECNICO: manutenção/equipamentos + despesa do serviço; sem financeiro completo.
        esperar(get("/api/manutencao/equipamentos"), tecnico, 200);
        esperar(get("/api/manutencao/chamados"), tecnico, 200);
        esperar(get("/api/financeiro/receitas"), tecnico, 403);
        mockMvc.perform(post("/api/financeiro/despesas/manutencao")
                        .header("Authorization", bearer(tokenDe(tecnico)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("descricao", "Troca de correia", "valor", 150.0))))
                .andExpect(status().isOk());

        // RECEPCIONISTA: alunos/cadastro, mensalidades, presença e solicitar manutenção.
        esperar(get("/api/aluno"), recepcao, 200);
        esperar(get("/api/mensalidade"), recepcao, 200);
        esperar(get("/api/frequencia/token"), recepcao, 200);
        esperar(get("/api/manutencao/equipamentos"), recepcao, 200);
        esperar(get("/api/manutencao/chamados"), recepcao, 403);

        // Perfil é acessível a qualquer usuário autenticado.
        esperar(get("/api/usuario"), tecnico, 200);
        esperar(get("/api/usuario"), financeiro, 200);
    }

    private void esperar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requisicao,
                         Usuario usuario, int statusEsperado) throws Exception {
        mockMvc.perform(requisicao.header("Authorization", bearer(tokenDe(usuario))))
                .andExpect(status().is(statusEsperado));
    }
}
