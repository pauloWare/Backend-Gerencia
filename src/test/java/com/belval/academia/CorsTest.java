package com.belval.academia;

import com.belval.academia.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS: o frontend roda em uma origem diferente da API (Vite na 5173, Expo
 * Web/Metro na 8081, Expo Go no IP local da máquina e Vercel em produção), então
 * o navegador faz preflight. Estes testes garantem que:
 *
 * <ul>
 *   <li>o preflight das origens de desenvolvimento é aceito e devolve os
 *       cabeçalhos de origem/métodos necessários ao login;</li>
 *   <li>origem desconhecida continua bloqueada (não usamos {@code "*"});</li>
 *   <li>o login segue público (sem JWT) e as rotas protegidas continuam
 *       exigindo token, mesmo com origem permitida.</li>
 * </ul>
 */
class CorsTest extends TesteBase {

    /** Vite: frontend web em desenvolvimento. */
    private static final String ORIGEM_VITE = "http://localhost:5173";
    /** Expo Web/Metro no IP local da máquina (origem que gerava o 403 corrigido). */
    private static final String ORIGEM_EXPO_WEB = "http://192.168.0.32:8081";
    private static final String ORIGEM_NAO_AUTORIZADA = "http://site-nao-autorizado.example";

    @Test
    void preflightDoExpoWebEhAceitoComOrigemMetodosECabecalhosNecessarios() throws Exception {
        MvcResult resultado = mockMvc.perform(options("/api/usuario/login")
                        .header(HttpHeaders.ORIGIN, ORIGEM_EXPO_WEB)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(resultado.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo(ORIGEM_EXPO_WEB);
        assertThat(resultado.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                .contains("POST")
                .contains("OPTIONS");
        assertThat(resultado.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .containsIgnoringCase("content-type")
                .containsIgnoringCase("authorization");
    }

    @Test
    void preflightDoViteEhAceito() throws Exception {
        MvcResult resultado = mockMvc.perform(options("/api/usuario/login")
                        .header(HttpHeaders.ORIGIN, ORIGEM_VITE)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(resultado.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo(ORIGEM_VITE);
    }

    @Test
    void preflightDeOrigemNaoAutorizadaContinuaBloqueado() throws Exception {
        MvcResult resultado = mockMvc.perform(options("/api/usuario/login")
                        .header(HttpHeaders.ORIGIN, ORIGEM_NAO_AUTORIZADA)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andReturn();

        // Sem permissão de CORS: nenhum cabeçalho de liberação é enviado.
        assertThat(resultado.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void loginSemJwtEhAceitoNaOrigemDoExpoWebEParteDoContratoSegueIgual() throws Exception {
        Usuario u = criarUsuario(emailUnico("cors-login"), "SenhaForte123", "ADMIN");

        MvcResult resultado = mockMvc.perform(post("/api/usuario/login")
                        .header(HttpHeaders.ORIGIN, ORIGEM_EXPO_WEB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", u.getEmail(), "senha", "SenhaForte123"))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(resultado.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo(ORIGEM_EXPO_WEB);
        // O login continua devolvendo "token" (valor conferido sem imprimi-lo).
        assertThat(objectMapper.readTree(resultado.getResponse().getContentAsString())
                .path("token").asText()).isNotBlank();
    }

    @Test
    void credenciaisInvalidasContinuamRejeitadasComOrigemPermitida() throws Exception {
        Usuario u = criarUsuario(emailUnico("cors-invalido"), "SenhaForte123", "ADMIN");

        mockMvc.perform(post("/api/usuario/login")
                        .header(HttpHeaders.ORIGIN, ORIGEM_EXPO_WEB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", u.getEmail(), "senha", "senha-errada"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginDeOrigemNaoAutorizadaEhBloqueadoAntesDoController() throws Exception {
        Usuario u = criarUsuario(emailUnico("cors-bloqueado"), "SenhaForte123", "ADMIN");

        // Mesmo com credenciais válidas, a origem desconhecida é recusada pelo CORS.
        mockMvc.perform(post("/api/usuario/login")
                        .header(HttpHeaders.ORIGIN, ORIGEM_NAO_AUTORIZADA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", u.getEmail(), "senha", "SenhaForte123"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void endpointProtegidoContinuaExigindoJwtComOrigemPermitida() throws Exception {
        mockMvc.perform(get("/api/aluno").header(HttpHeaders.ORIGIN, ORIGEM_EXPO_WEB))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoContinuaAcessandoEndpointProtegidoSemOrigin() throws Exception {
        // Requisições nativas (Expo Go no celular / curl) não enviam Origin e
        // continuam funcionando normalmente com o token.
        Usuario admin = criarUsuario(emailUnico("cors-protegido"), "SenhaForte123", "ADMIN");

        mockMvc.perform(get("/api/aluno").header("Authorization", bearer(tokenDe(admin))))
                .andExpect(status().isOk());
    }
}
