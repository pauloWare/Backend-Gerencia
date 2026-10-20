package com.belval.academia;

import com.belval.academia.model.Usuario;
import com.belval.academia.repository.UsuarioRepository;
import com.belval.academia.security.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de API: MockMvc + dados únicos por teste.
 *
 * <p>Os testes usam H2 em memória (src/test/resources/application.properties) e
 * assinam tokens com o mesmo {@link TokenService} da aplicação, validando de
 * fato assinatura, expiração e permissões.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class TesteBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected TokenService tokenService;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** E-mail único por execução, evitando colisão entre testes. */
    protected static String emailUnico(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
    }

    /** Cria um usuário ativo com a senha já hasheada. */
    protected Usuario criarUsuario(String email, String senha, String cargo) {
        Usuario u = new Usuario();
        u.setNome("Usuário " + cargo);
        u.setEmail(email.toLowerCase());
        u.setSenha(passwordEncoder.encode(senha));
        u.setCargo(cargo);
        u.setStatus("user");
        return usuarioRepository.save(u);
    }

    /** Token válido para o usuário (o mesmo formato devolvido pelo login). */
    protected String tokenDe(Usuario usuario) {
        return tokenService.gerarToken(usuario);
    }

    protected String tokenExpiradoDe(Usuario usuario) {
        return tokenService.gerarToken(usuario, Duration.ofMinutes(-5));
    }

    /** Faz login de verdade e devolve o token emitido pelo endpoint. */
    protected String tokenPorLogin(String email, String senha) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "senha", senha))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString())
                .get("token").asText();
    }

    protected String json(Object valor) throws Exception {
        return objectMapper.writeValueAsString(valor);
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    // ===================== CPF de teste =====================

    /** Gera um CPF válido e diferente a cada chamada (dígitos verificadores corretos). */
    protected static String cpfValidoAleatorio() {
        java.util.Random aleatorio = new java.util.Random();
        StringBuilder base = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            base.append(aleatorio.nextInt(10));
        }
        if (base.chars().distinct().count() == 1) {
            base.setCharAt(8, '7');
        }
        return cpfValido(base.toString());
    }

    /** Completa os dois dígitos verificadores de uma base de 9 dígitos. */
    protected static String cpfValido(String base9) {
        String comPrimeiro = base9 + digitoVerificador(base9, 10);
        return comPrimeiro + digitoVerificador(comPrimeiro, 11);
    }

    // ===================== Telefone de teste =====================

    /**
     * Sequência estática: telefones sempre diferentes dentro da mesma JVM,
     * sem risco de colidir no índice único compartilhado por todos os testes.
     * 11 dígitos começando por 9 — nunca cai na regra de remoção do código
     * do país 55 (que só se aplica a 12/13 dígitos).
     */
    private static final java.util.concurrent.atomic.AtomicLong SEQUENCIA_TELEFONE =
            new java.util.concurrent.atomic.AtomicLong(91000000000L);

    /** Telefone único por chamada (11 dígitos), para os testes não colidirem. */
    protected static String telefoneUnico() {
        return Long.toString(SEQUENCIA_TELEFONE.incrementAndGet());
    }

    private static int digitoVerificador(String numeros, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < numeros.length(); i++) {
            soma += (numeros.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
