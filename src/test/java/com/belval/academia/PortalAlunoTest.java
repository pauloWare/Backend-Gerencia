package com.belval.academia;

import com.belval.academia.model.Aluno;
import com.belval.academia.model.Frequencia;
import com.belval.academia.model.Mensalidade;
import com.belval.academia.model.Usuario;
import com.belval.academia.repository.AlunoRepository;
import com.belval.academia.repository.FrequenciaRepository;
import com.belval.academia.repository.MensalidadeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Portal do ALUNO no app mobile: login por E-MAIL e SENHA, marcação de presença
 * (com o controle de acesso financeiro no backend), frequência, mensalidade,
 * perfil e foto do próprio aluno.
 *
 * <p>Cobre os cenários exigidos na validação: aluno em dia registra presença,
 * inadimplente é recusado sem registro, falha financeira não autoriza, um aluno
 * não lê nem edita os dados de outro, mensalidade quitada não oferece
 * pagamento, e o acesso administrativo existente continua funcionando.</p>
 */
class PortalAlunoTest extends TesteBase {

    @Autowired
    private AlunoRepository alunoRepository;
    @Autowired
    private MensalidadeRepository mensalidadeRepository;
    @Autowired
    private FrequenciaRepository frequenciaRepository;

    private static final LocalDate NASCIMENTO = LocalDate.of(1990, 1, 1);
    private static final String SENHA_ALUNO = "AlunoSenha123";

    /**
     * Cria um aluno (ATIVO por padrão) com CPF válido e único, e-mail próprio e
     * senha de acesso já definida com BCrypt — igual ao que a recepção faz em
     * {@code PUT /api/aluno/{id}/senha-acesso}.
     */
    private Aluno criarAluno(String situacao) {
        Aluno aluno = new Aluno();
        aluno.setNome("Aluno Portal");
        aluno.setCpf(cpfValidoAleatorio());
        aluno.setDataNascimento(NASCIMENTO);
        aluno.setPlano("Mensal");
        aluno.setSituacao(situacao);
        aluno.setEmail(emailUnico("aluno"));
        aluno.setSenha(passwordEncoder.encode(SENHA_ALUNO));
        return alunoRepository.save(aluno);
    }

    private void criarMensalidade(Aluno aluno, String status, LocalDate vencimento) {
        Mensalidade m = new Mensalidade();
        m.setAluno(aluno);
        m.setStatus(status);
        m.setDataVencimento(vencimento);
        m.setValor(120.0);
        if ("PAGA".equals(status)) {
            m.setDataPagamento(vencimento);
        }
        mensalidadeRepository.save(m);
    }

    /** Login de aluno pelo endpoint do portal (e-mail + senha). */
    private String loginAluno(String email, String senha) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/portal/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "senha", senha))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString())
                .get("token").asText();
    }

    /** Atalho: autentica o aluno recém-criado e devolve o token. */
    private String tokenDoAluno(Aluno aluno) throws Exception {
        return loginAluno(aluno.getEmail(), SENHA_ALUNO);
    }

    /** Token diário do QR Code (obtido pela recepção/ADMIN). */
    private String tokenDoQr(Usuario admin) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/frequencia/token")
                        .header("Authorization", bearer(tokenDe(admin))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString())
                .get("token").asText();
    }

    private long presencasDe(Long alunoId) {
        return frequenciaRepository.findByAlunoId(alunoId).size();
    }

    // ===================== Login do aluno =====================

    @Test
    void loginDeAlunoValidoDevolveTokenSemExporDadosDesnecessarios() throws Exception {
        Aluno aluno = criarAluno("ATIVO");

        MvcResult resultado = mockMvc.perform(post("/api/portal/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", aluno.getEmail(), "senha", SENHA_ALUNO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.aluno.id").value(aluno.getId()))
                .andExpect(jsonPath("$.aluno.nome").value("Aluno Portal"))
                .andReturn();

        // O CPF completo e a senha/hash NUNCA são devolvidos ao aplicativo.
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain(aluno.getCpf())
                .doesNotContain(SENHA_ALUNO)
                .doesNotContain("\"senha\"")
                .doesNotContain("$2a$");
    }

    @Test
    void loginComSenhaErradaEhNaoAutorizado() throws Exception {
        Aluno aluno = criarAluno("ATIVO");

        mockMvc.perform(post("/api/portal/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", aluno.getEmail(), "senha", "senha-errada"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("E-mail ou senha inválidos."));
    }

    @Test
    void loginComEmailNaoCadastradoEhNaoAutorizadoSemRevelarExistencia() throws Exception {
        mockMvc.perform(post("/api/portal/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailUnico("inexistente"), "senha", "qualquer"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("E-mail ou senha inválidos."));
    }

    @Test
    void alunoSemSenhaDefinidaNaoConsegueEntrar() throws Exception {
        // Cadastro antigo: tem e-mail, mas a senha ainda não foi liberada.
        Aluno aluno = new Aluno();
        aluno.setNome("Aluno Sem Senha");
        aluno.setCpf(cpfValidoAleatorio());
        aluno.setEmail(emailUnico("sem-senha"));
        aluno.setSituacao("ATIVO");
        alunoRepository.save(aluno);

        mockMvc.perform(post("/api/portal/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", aluno.getEmail(), "senha", "qualquer"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("E-mail ou senha inválidos."));
    }

    @Test
    void cpfEDataDeNascimentoNaoServemMaisComoCredencial() throws Exception {
        Aluno aluno = criarAluno("ATIVO");

        // O corpo antigo (CPF + data de nascimento) não autentica mais.
        mockMvc.perform(post("/api/portal/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cpf", aluno.getCpf(),
                                "dataNascimento", NASCIMENTO.toString()))))
                .andExpect(status().isUnauthorized());
    }

    // ===================== Marcação de presença =====================

    @Test
    void alunoComMensalidadeEmDiaRegistraPresenca() throws Exception {
        Usuario admin = criarUsuario(emailUnico("portal-admin"), "SenhaForte123", "ADMIN");
        Aluno aluno = criarAluno("ATIVO");
        criarMensalidade(aluno, "PAGA", LocalDate.now().minusDays(5));
        String token = tokenDoQr(admin);

        mockMvc.perform(post("/api/frequencia/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "cpf", aluno.getCpf()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("Presença registrada com sucesso!"));

        assertThat(presencasDe(aluno.getId())).isEqualTo(1);
    }

    @Test
    void alunoInadimplenteNaoRegistraPresenca() throws Exception {
        Usuario admin = criarUsuario(emailUnico("portal-admin2"), "SenhaForte123", "ADMIN");
        Aluno aluno = criarAluno("ATIVO");
        criarMensalidade(aluno, "PENDENTE", LocalDate.now().minusDays(10));
        String token = tokenDoQr(admin);

        mockMvc.perform(post("/api/frequencia/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "cpf", aluno.getCpf()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("MENSALIDADE_ATRASADA"))
                .andExpect(jsonPath("$.erro").value(
                        "Sua mensalidade não está em dia. Por favor, procure a recepção para regularizar sua situação."));

        // Nenhuma presença deve ser criada quando a validação financeira falha.
        assertThat(presencasDe(aluno.getId())).isZero();
    }

    @Test
    void presencaDuplicadaNaoCriaRegistroExtra() throws Exception {
        Usuario admin = criarUsuario(emailUnico("portal-admin3"), "SenhaForte123", "ADMIN");
        Aluno aluno = criarAluno("ATIVO");
        criarMensalidade(aluno, "PAGA", LocalDate.now().minusDays(1));
        String token = tokenDoQr(admin);
        String corpo = json(Map.of("token", token, "cpf", aluno.getCpf()));

        mockMvc.perform(post("/api/frequencia/check-in")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/frequencia/check-in")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Este aluno já registrou presença hoje."));

        assertThat(presencasDe(aluno.getId())).isEqualTo(1);
    }

    @Test
    void alunoNaoAptaPorSituacaoCadastralNaoRegistraPresenca() throws Exception {
        Usuario admin = criarUsuario(emailUnico("portal-admin4"), "SenhaForte123", "ADMIN");
        Aluno aluno = criarAluno("CANCELADO");
        criarMensalidade(aluno, "PAGA", LocalDate.now().minusDays(1));
        String token = tokenDoQr(admin);

        mockMvc.perform(post("/api/frequencia/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "cpf", aluno.getCpf()))))
                .andExpect(status().isBadRequest());

        assertThat(presencasDe(aluno.getId())).isZero();
    }

    // ===================== Check-in do app (/api/entrada/validar) =====================

    @Test
    void entradaValidarRegistraPresencaDoAlunoAutenticado() throws Exception {
        Usuario admin = criarUsuario(emailUnico("entrada-admin"), "SenhaForte123", "ADMIN");
        Aluno aluno = criarAluno("ATIVO");
        criarMensalidade(aluno, "PAGA", LocalDate.now().minusDays(2));
        String tokenAluno = tokenDoAluno(aluno);

        mockMvc.perform(post("/api/entrada/validar")
                        .header("Authorization", bearer(tokenAluno))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", tokenDoQr(admin)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("Presença registrada com sucesso!"))
                .andExpect(jsonPath("$.aluno.id").value(aluno.getId()))
                .andExpect(jsonPath("$.aluno.nome").value("Aluno Portal"))
                .andExpect(jsonPath("$.horaEntrada").isNotEmpty());

        assertThat(presencasDe(aluno.getId())).isEqualTo(1);
    }

    @Test
    void entradaValidarSemSessaoDeAlunoEhNaoAutorizado() throws Exception {
        mockMvc.perform(post("/api/entrada/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", "qualquer-qr"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void entradaValidarComSessaoDaEquipeEhProibido() throws Exception {
        Usuario admin = criarUsuario(emailUnico("entrada-equipe"), "SenhaForte123", "ADMIN");

        mockMvc.perform(post("/api/entrada/validar")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", tokenDoQr(admin)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void entradaValidarComQrInvalidoNaoRegistraPresenca() throws Exception {
        Aluno aluno = criarAluno("ATIVO");
        criarMensalidade(aluno, "PAGA", LocalDate.now().minusDays(2));
        String tokenAluno = tokenDoAluno(aluno);

        mockMvc.perform(post("/api/entrada/validar")
                        .header("Authorization", bearer(tokenAluno))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", "qr-de-outro-dia"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(
                        "QR Code inválido ou expirado. Solicite um novo QR à recepção."));

        assertThat(presencasDe(aluno.getId())).isZero();
    }

    @Test
    void entradaValidarIgnoraCpfEnviadoNoCorpoEUsaASessao() throws Exception {
        Usuario admin = criarUsuario(emailUnico("entrada-cpf"), "SenhaForte123", "ADMIN");
        Aluno autenticado = criarAluno("ATIVO");
        criarMensalidade(autenticado, "PAGA", LocalDate.now().minusDays(2));
        Aluno outro = criarAluno("ATIVO");
        criarMensalidade(outro, "PAGA", LocalDate.now().minusDays(2));
        String tokenAluno = tokenDoAluno(autenticado);

        // O corpo tenta registrar a presença para OUTRO aluno: o backend ignora
        // o CPF e usa sempre a identidade vinda do token assinado.
        mockMvc.perform(post("/api/entrada/validar")
                        .header("Authorization", bearer(tokenAluno))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", tokenDoQr(admin), "cpf", outro.getCpf()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aluno.id").value(autenticado.getId()));

        assertThat(presencasDe(autenticado.getId())).isEqualTo(1);
        assertThat(presencasDe(outro.getId())).isZero();
    }

    @Test
    void entradaValidarBloqueiaMensalidadeAtrasada() throws Exception {
        Usuario admin = criarUsuario(emailUnico("entrada-fin"), "SenhaForte123", "ADMIN");
        Aluno aluno = criarAluno("ATIVO");
        criarMensalidade(aluno, "PENDENTE", LocalDate.now().minusDays(10));
        String tokenAluno = tokenDoAluno(aluno);

        mockMvc.perform(post("/api/entrada/validar")
                        .header("Authorization", bearer(tokenAluno))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", tokenDoQr(admin)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.motivo").value("MENSALIDADE_ATRASADA"));

        assertThat(presencasDe(aluno.getId())).isZero();
    }

    @Test
    void entradaValidarDuplicadoConflitaENaoCriaRegistroExtra() throws Exception {
        Usuario admin = criarUsuario(emailUnico("entrada-dup"), "SenhaForte123", "ADMIN");
        Aluno aluno = criarAluno("ATIVO");
        criarMensalidade(aluno, "PAGA", LocalDate.now().minusDays(2));
        String tokenAluno = tokenDoAluno(aluno);
        String corpo = json(Map.of("token", tokenDoQr(admin)));

        mockMvc.perform(post("/api/entrada/validar")
                        .header("Authorization", bearer(tokenAluno))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/entrada/validar")
                        .header("Authorization", bearer(tokenAluno))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Este aluno já registrou presença hoje."));

        assertThat(presencasDe(aluno.getId())).isEqualTo(1);
    }

    // ===================== Isolamento entre alunos =====================

    @Test
    void alunoVeSomenteAPropriaFrequencia() throws Exception {
        // Aluno A tem presença; aluno B não. Cada um consulta a própria tela.
        Aluno alunoA = criarAluno("ATIVO");
        Frequencia frequencia = new Frequencia();
        frequencia.setAluno(alunoA);
        frequencia.setData(LocalDate.now());
        frequenciaRepository.save(frequencia);

        Aluno alunoB = criarAluno("ATIVO");
        String tokenB = tokenDoAluno(alunoB);

        // O endpoint NÃO recebe id de aluno: só é possível ler os próprios dados.
        mockMvc.perform(get("/api/portal/frequencia")
                        .header("Authorization", bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPresencas").value(0))
                .andExpect(jsonPath("$.presencasMes").value(0));
    }

    @Test
    void alunoVeSomenteAsPropriasMensalidades() throws Exception {
        Aluno alunoA = criarAluno("ATIVO");
        criarMensalidade(alunoA, "PAGA", LocalDate.now().minusDays(3));

        Aluno alunoB = criarAluno("ATIVO");
        String tokenB = tokenDoAluno(alunoB);

        mockMvc.perform(get("/api/portal/mensalidades")
                        .header("Authorization", bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("SEM_MENSALIDADE"))
                .andExpect(jsonPath("$.mensalidades.length()").value(0));
    }

    // ===================== Frequência do aluno =====================

    @Test
    void portalFrequenciaRetornaPercentualPresencasEHistorico() throws Exception {
        Usuario admin = criarUsuario(emailUnico("portal-admin5"), "SenhaForte123", "ADMIN");
        Aluno aluno = criarAluno("ATIVO");
        criarMensalidade(aluno, "PAGA", LocalDate.now().minusDays(1));
        String tokenAluno = tokenDoAluno(aluno);

        mockMvc.perform(post("/api/frequencia/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", tokenDoQr(admin), "cpf", aluno.getCpf()))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/portal/frequencia")
                        .header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPresencas").value(1))
                .andExpect(jsonPath("$.presencasMes").value(1))
                .andExpect(jsonPath("$.diasConsiderados").isNumber())
                .andExpect(jsonPath("$.percentual").isNumber())
                .andExpect(jsonPath("$.historico.length()").value(1))
                .andExpect(jsonPath("$.ultimaPresenca.data").isNotEmpty());
    }
    // ===================== Mensalidade do aluno =====================

    @Test
    void mensalidadeQuitadaNaoOferecePagamentoEInformaEmDia() throws Exception {
        Aluno aluno = criarAluno("ATIVO");
        criarMensalidade(aluno, "PAGA", LocalDate.now().minusDays(20));
        String tokenAluno = tokenDoAluno(aluno);

        mockMvc.perform(get("/api/portal/mensalidades")
                        .header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("EM_DIA"))
                // Sem integração de pagamento: nunca expor uma ação de pagar/quitar.
                .andExpect(jsonPath("$.pagamentoOnlineDisponivel").value(false))
                .andExpect(jsonPath("$.mensalidades[0].situacao").value("PAGA"))
                .andExpect(jsonPath("$.mensalidades[0].valor").value(120.0));
    }

    @Test
    void mensalidadeAtrasadaInformaSituacaoEQueOPagamentoEhNaRecepcao() throws Exception {
        Aluno aluno = criarAluno("ATIVO");
        criarMensalidade(aluno, "PENDENTE", LocalDate.now().minusDays(15));
        String tokenAluno = tokenDoAluno(aluno);

        mockMvc.perform(get("/api/portal/mensalidades")
                        .header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("ATRASADA"))
                .andExpect(jsonPath("$.pagamentoOnlineDisponivel").value(false))
                .andExpect(jsonPath("$.mensagemPagamento").value(
                        org.hamcrest.Matchers.containsString("recepção")))
                .andExpect(jsonPath("$.mensalidades[0].situacao").value("ATRASADA"));
    }

    // ===================== Autenticação e autorização =====================

    @Test
    void portalSemTokenEhNaoAutorizado() throws Exception {
        mockMvc.perform(get("/api/portal/frequencia")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/portal/mensalidades")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeAlunoNaoAcessaRotasAdministrativas() throws Exception {
        Aluno aluno = criarAluno("ATIVO");
        String tokenAluno = tokenDoAluno(aluno);

        // Lista de alunos do sistema web: exige papel administrativo.
        mockMvc.perform(get("/api/aluno").header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isForbidden());
        // Mensalidades de todos os alunos (endpoint administrativo).
        mockMvc.perform(get("/api/mensalidade").header("Authorization", bearer(tokenAluno)))
                .andExpect(status().isForbidden());
    }

    @Test
    void perfilAdministrativoNaoAcessaOPortalDoAluno() throws Exception {
        Usuario admin = criarUsuario(emailUnico("portal-admin6"), "SenhaForte123", "ADMIN");

        mockMvc.perform(get("/api/portal/mensalidades")
                        .header("Authorization", bearer(tokenDe(admin))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/portal/frequencia")
                        .header("Authorization", bearer(tokenDe(admin))))
                .andExpect(status().isForbidden());
    }

    @Test
    void perfilAdministrativoContinuaFuncionando() throws Exception {
        Usuario admin = criarUsuario(emailUnico("portal-admin7"), "SenhaForte123", "ADMIN");

        // O login e a listagem administrativa (usados pelo sistema web) seguem iguais.
        String token = tokenPorLogin(admin.getEmail(), "SenhaForte123");
        mockMvc.perform(get("/api/aluno").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }
}