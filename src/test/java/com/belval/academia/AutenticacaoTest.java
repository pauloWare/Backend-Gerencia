package com.belval.academia;

import com.belval.academia.model.Usuario;
import com.belval.academia.service.AutenticacaoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Senhas: armazenamento com BCrypt, login e não exposição nas respostas. */
class AutenticacaoTest extends TesteBase {

    @Test
    void loginComSenhaCorretaDevolveToken() throws Exception {
        Usuario u = criarUsuario(emailUnico("login-ok"), "SenhaForte123", "ADMIN");

        mockMvc.perform(post("/api/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", u.getEmail(), "senha", "SenhaForte123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.email").value(u.getEmail()))
                .andExpect(jsonPath("$.usuario.cargo").value("ADMIN"));
    }

    @Test
    void loginComSenhaIncorretaRetorna401() throws Exception {
        Usuario u = criarUsuario(emailUnico("login-erro"), "SenhaForte123", "ADMIN");

        mockMvc.perform(post("/api/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", u.getEmail(), "senha", "senha-errada"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Email ou senha inválidos."));
    }

    @Test
    void loginDeEmailInexistenteRetorna401ComMensagemGenerica() throws Exception {
        mockMvc.perform(post("/api/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailUnico("nao-existe"), "senha", "qualquer"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Email ou senha inválidos."));
    }

    @Test
    void respostasDeLoginENaoExpoemSenhaNemHash() throws Exception {
        Usuario u = criarUsuario(emailUnico("sem-senha"), "SenhaForte123", "ADMIN");

        MvcResult login = mockMvc.perform(post("/api/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", u.getEmail(), "senha", "SenhaForte123"))))
                .andExpect(status().isOk())
                .andReturn();
        String corpoLogin = login.getResponse().getContentAsString();
        // Chave "senha" nunca pode aparecer (o valor do e-mail de teste pode conter
        // a substring "senha", por isso a verificação usa a chave JSON entre aspas).
        assertThat(corpoLogin).doesNotContain("\"senha\"").doesNotContain("$2a$").doesNotContain("$2b$");

        MvcResult listagem = mockMvc.perform(get("/api/usuario").header("Authorization", bearer(tokenDe(u))))
                .andExpect(status().isOk())
                .andReturn();
        String corpoListagem = listagem.getResponse().getContentAsString();
        assertThat(corpoListagem).doesNotContain("\"senha\"").doesNotContain("$2a$").doesNotContain("$2b$");
    }

    @Test
    void cadastroAdministrativoGravaHashBcryptENaoDevolveSenha() throws Exception {
        Usuario admin = criarUsuario(emailUnico("admin"), "SenhaForte123", "ADMIN");
        String emailNovo = emailUnico("novo");

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", "Nova Pessoa");
        corpo.put("email", emailNovo);
        corpo.put("senha", "OutraSenha456");
        corpo.put("cargo", "RECEPCIONISTA");

        MvcResult resultado = mockMvc.perform(post("/api/usuario")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(corpo)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("OutraSenha456");

        Usuario salvo = usuarioRepository.findByEmailIgnoreCase(emailNovo).orElseThrow();
        assertThat(salvo.getSenha()).isNotEqualTo("OutraSenha456");
        assertThat(AutenticacaoService.ehHashBcrypt(salvo.getSenha())).isTrue();
        assertThat(passwordEncoder.matches("OutraSenha456", salvo.getSenha())).isTrue();
    }

    @Test
    void senhaLegadaEmTextoPuroEhMigradaParaBcryptNoPrimeiroLogin() throws Exception {
        String email = emailUnico("legado");
        Usuario legado = new Usuario();
        legado.setNome("Usuário Legado");
        legado.setEmail(email);
        legado.setSenha("Gerencia@123"); // como estão os usuários antigos no banco
        legado.setCargo("FINANCEIRO");
        legado.setStatus("user");
        usuarioRepository.save(legado);

        tokenPorLogin(email, "Gerencia@123");

        Usuario migrado = usuarioRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(AutenticacaoService.ehHashBcrypt(migrado.getSenha())).isTrue();
        assertThat(passwordEncoder.matches("Gerencia@123", migrado.getSenha())).isTrue();

        // Continua entrando normalmente depois da migração.
        tokenPorLogin(email, "Gerencia@123");

        // E senha errada segue bloqueada.
        mockMvc.perform(post("/api/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "senha", "errada"))))
                .andExpect(status().isUnauthorized());
    }
}
