package com.belval.academia;

import com.belval.academia.model.Aluno;
import com.belval.academia.model.Usuario;
import com.belval.academia.repository.AlunoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login unificado ({@code POST /api/auth/login}): a tela do aplicativo passou a
 * pedir apenas e-mail e senha, e o backend identifica sozinho se a conta é da
 * equipe ou de um aluno.
 *
 * <p>Valida o roteamento por cargo e as regras de segurança: mensagem genérica
 * para credencial inválida, ausência de senha/hash nas respostas, isolamento entre
 * áreas e preservação do contrato antigo do web ({@code /api/usuario/login}).</p>
 */
class LoginUnificadoTest extends TesteBase {

    @Autowired
    private AlunoRepository alunoRepository;

    private static final String SENHA_ALUNO = "AlunoSenha123";

    /** Aluno com senha de acesso definida (como a recepção libera). */
    private Aluno criarAlunoComSenha() {
        Aluno aluno = new Aluno();
        aluno.setNome("Aluno Unificado");
        aluno.setCpf(cpfValidoAleatorio());
        aluno.setDataNascimento(LocalDate.of(1990, 1, 1));
        aluno.setEmail(emailUnico("aluno-unif"));
        aluno.setPlano("Mensal");
        aluno.setSituacao("ATIVO");
        aluno.setSenha(passwordEncoder.encode(SENHA_ALUNO));
        return alunoRepository.save(aluno);
    }

    private MvcResult login(String email, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "senha", senha))))
                .andReturn();
    }

    /** Token do aluno via o login unificado. */
    private String tokenDoAluno(Aluno aluno) throws Exception {
        return objectMapper.readTree(login(aluno.getEmail(), SENHA_ALUNO)
                        .getResponse().getContentAsString())
                .get("token").asText();
    }

    // ==================== Login válido por cargo ====================

    @Test
    void loginDeAdminRetornaCargoAdminERotaDaEquipe() throws Exception {
        Usuario admin = criarUsuario(emailUnico("unif-admin"), "SenhaForte123", "ADMIN");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", admin.getEmail(), "senha", "SenhaForte123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.cargo").value("ADMIN"))
                .andExpect(jsonPath("$.usuario.email").value(admin.getEmail()))
                .andExpect(jsonPath("$.aluno").doesNotExist());
    }

    @Test
    void loginDeTecnicoRetornaCargoTecnico() throws Exception {
        Usuario tecnico = criarUsuario(emailUnico("unif-tec"), "SenhaForte123", "TECNICO");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", tecnico.getEmail(), "senha", "SenhaForte123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargo").value("TECNICO"))
                .andExpect(jsonPath("$.usuario.cargo").value("TECNICO"));
    }

    @Test
    void loginDeAlunoRetornaCargoAlunoSemDadosDaEquipe() throws Exception {
        Aluno aluno = criarAlunoComSenha();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", aluno.getEmail(), "senha", SENHA_ALUNO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.cargo").value("ALUNO"))
                .andExpect(jsonPath("$.aluno.id").value(aluno.getId()))
                .andExpect(jsonPath("$.aluno.nome").value("Aluno Unificado"))
                .andExpect(jsonPath("$.usuario").doesNotExist());
    }

    @Test
    void loginDeRecepcionistaAutenticaMasNaoEntraNoPortalDoAluno() throws Exception {
        // Cargo legítimo do sistema: autentica normalmente, mas o app não tem área
        // para ele — e as permissões continuam sendo as do SecurityConfig.
        Usuario recepcao = criarUsuario(emailUnico("unif-rec"), "SenhaForte123", "RECEPCIONISTA");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", recepcao.getEmail(), "senha", "SenhaForte123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargo").value("RECEPCIONISTA"));

        String token = objectMapper.readTree(login(recepcao.getEmail(), "SenhaForte123")
                        .getResponse().getContentAsString())
                .get("token").asText();

        mockMvc.perform(get("/api/portal/perfil").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    // ==================== Falhas de credencial ====================

    @Test
    void senhaIncorretaRetorna401ComMensagemGenerica() throws Exception {
        Usuario admin = criarUsuario(emailUnico("unif-senha"), "SenhaForte123", "ADMIN");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", admin.getEmail(), "senha", "errada"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("E-mail ou senha inválidos."));
    }

    @Test
    void usuarioInexistenteRetornaAMesmaMensagemDeSenhaIncorreta() throws Exception {
        // A resposta não pode revelar se o e-mail está cadastrado.
        String emailNaoCadastrado = emailUnico("unif-inexistente");
        MvcResult semCadastro = login(emailNaoCadastrado, "qualquer");

        Usuario existente = criarUsuario(emailUnico("unif-existe"), "SenhaForte123", "ADMIN");
        MvcResult comCadastro = login(existente.getEmail(), "qualquer");

        assertThat(semCadastro.getResponse().getStatus()).isEqualTo(401);
        assertThat(comCadastro.getResponse().getStatus()).isEqualTo(401);
        assertThat(semCadastro.getResponse().getContentAsString())
                .isEqualTo(comCadastro.getResponse().getContentAsString());
    }

    @Test
    void emailAusenteOuInvalidoRetorna401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("senha", "SenhaForte123"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nao-e-email", "senha", "SenhaForte123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void alunoSemSenhaNaoAutenticaPeloLoginUnificado() throws Exception {
        Aluno aluno = new Aluno();
        aluno.setNome("Sem Senha");
        aluno.setCpf(cpfValidoAleatorio());
        aluno.setEmail(emailUnico("unif-sem-senha"));
        aluno.setSituacao("ATIVO");
        alunoRepository.save(aluno);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", aluno.getEmail(), "senha", "qualquer"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("E-mail ou senha inválidos."));
    }

    @Test
    void respostaDeLoginNuncaExpoeSenhaNemHash() throws Exception {
        Usuario admin = criarUsuario(emailUnico("unif-expoe"), "SenhaForte123", "ADMIN");
        Aluno aluno = criarAlunoComSenha();

        for (String[] credencial : new String[][]{
                {admin.getEmail(), "SenhaForte123"},
                {aluno.getEmail(), SENHA_ALUNO}}) {
            MvcResult resultado = login(credencial[0], credencial[1]);
            assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
            String corpo = resultado.getResponse().getContentAsString();
            assertThat(corpo)
                    .doesNotContain(credencial[1])
                    .doesNotContain("\"senha\"")
                    .doesNotContain("$2a$")
                    .doesNotContain("$2b$");
        }
    }

    // ==================== Sessão e isolamento ====================

    @Test
    void sessaoValidaDevolveOCargoDoTokenSemExporSenha() throws Exception {
        Aluno aluno = criarAlunoComSenha();

        mockMvc.perform(get("/api/auth/sessao").header("Authorization", bearer(tokenDoAluno(aluno))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargo").value("ALUNO"))
                .andExpect(jsonPath("$.aluno.id").value(aluno.getId()))
                .andExpect(jsonPath("$.usuario").doesNotExist());
    }

    @Test
    void sessaoDaEquipeContinuaRespondendoPeloCargo() throws Exception {
        Usuario tecnico = criarUsuario(emailUnico("unif-sessao"), "SenhaForte123", "TECNICO");

        mockMvc.perform(get("/api/auth/sessao").header("Authorization", bearer(tokenDe(tecnico))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargo").value("TECNICO"))
                .andExpect(jsonPath("$.usuario.email").value(tecnico.getEmail()));
    }

    @Test
    void tokenInvalidoOuExpiradoEhRecusado() throws Exception {
        mockMvc.perform(get("/api/auth/sessao").header("Authorization", bearer("token.invalido")))
                .andExpect(status().isUnauthorized());

        Usuario tecnico = criarUsuario(emailUnico("unif-expira"), "SenhaForte123", "TECNICO");
        mockMvc.perform(get("/api/auth/sessao")
                        .header("Authorization", bearer(tokenExpiradoDe(tecnico))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void alunoNaoAcessaRotasDaEquipeNemRedefineSenhaDeAluno() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDoAluno(aluno);

        mockMvc.perform(get("/api/aluno").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/usuario").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/aluno/" + aluno.getId() + "/senha-acesso")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("senha", "NovaSenha999"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void equipeNaoAcessaOportalDoAluno() throws Exception {
        Usuario admin = criarUsuario(emailUnico("unif-admin3"), "SenhaForte123", "ADMIN");
        Aluno aluno = criarAlunoComSenha();

        // /api/portal/** exige ROLE_ALUNO.
        mockMvc.perform(get("/api/portal/perfil").header("Authorization", bearer(tokenDe(admin))))
                .andExpect(status().isForbidden());

        // Já o perfil do aluno autenticado continua acessível a ele.
        mockMvc.perform(get("/api/portal/perfil")
                        .header("Authorization", bearer(tokenDoAluno(aluno))))
                .andExpect(status().isOk());
    }

    // ==================== Compatibilidade com o web ====================

    @Test
    void loginAntigoDaEquipeContinuaIgualParaOFrontendWeb() throws Exception {
        Usuario admin = criarUsuario(emailUnico("unif-web"), "SenhaForte123", "ADMIN");

        // Contrato consumido pelo web: { token, usuario } e a mesma mensagem 401.
        mockMvc.perform(post("/api/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", admin.getEmail(), "senha", "SenhaForte123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.cargo").value("ADMIN"));

        mockMvc.perform(post("/api/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", admin.getEmail(), "senha", "errada"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Email ou senha inválidos."));
    }
}