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
 * Página de PERFIL do aluno: leitura, edição dos dados pessoais permitidos
 * (nome, e-mail, telefone, CPF e data de nascimento) e foto de perfil com
 * persistência real no banco.
 *
 * <p>Cobre os cenários exigidos: carregar o perfil, editar e salvar, cancelar
 * edição (nenhuma requisição), validar campos (incluindo CPF inválido/duplicado
 * e data futura/inválida), confirmar persistência em nova consulta, impedir
 * edição do perfil ou do CPF de outro aluno, impedir alteração de campos
 * administrativos, manter sessão/login após troca de e-mail, além da
 * persistência da foto (selecionar, cancelar, salvar, reabrir, formato inválido
 * e tamanho).</p>
 */
class PerfilAlunoTest extends TesteBase {

    @Autowired
    private AlunoRepository alunoRepository;

    private static final String SENHA_ALUNO = "AlunoSenha123";
    /** Data URL de 1x1 pixel em PNG (Base64 válido e minúsculo). */
    private static final String FOTO_PNG = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    private Aluno criarAlunoComSenha() {
        Aluno aluno = new Aluno();
        aluno.setNome("Aluno Perfil");
        aluno.setCpf(cpfValidoAleatorio());
        aluno.setDataNascimento(LocalDate.of(1990, 5, 20));
        aluno.setEmail(emailUnico("perfil"));
        aluno.setTelefone("11987654321");
        aluno.setPlano("Mensal");
        aluno.setSituacao("ATIVO");
        aluno.setSenha(passwordEncoder.encode(SENHA_ALUNO));
        return alunoRepository.save(aluno);
    }

    private String tokenDe(Aluno aluno) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", aluno.getEmail(), "senha", SENHA_ALUNO))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString())
                .get("token").asText();
    }

    // ==================== Leitura do perfil ====================

    @Test
    void perfilDevolveOsDadosDoAlunoAutenticadoComCpfMascarado() throws Exception {
        Aluno aluno = criarAlunoComSenha();

        mockMvc.perform(get("/api/portal/perfil").header("Authorization", bearer(tokenDe(aluno))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aluno.getId()))
                .andExpect(jsonPath("$.nome").value("Aluno Perfil"))
                .andExpect(jsonPath("$.email").value(aluno.getEmail()))
                .andExpect(jsonPath("$.telefone").value("11987654321"))
                .andExpect(jsonPath("$.plano").value("Mensal"))
                .andExpect(jsonPath("$.situacao").value("ATIVO"))
                .andExpect(jsonPath("$.dataNascimento").value("1990-05-20"))
                // O CPF vem mascarado: o app exibe, mas não usa como credencial.
                .andExpect(jsonPath("$.cpfMascarado").value("***.***-" + aluno.getCpf().substring(9)));
    }

    @Test
    void perfilNaoExpoeSenhaHashNemDadosAdministrativos() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        MvcResult resultado = mockMvc.perform(get("/api/portal/perfil")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        String corpo = resultado.getResponse().getContentAsString();
        assertThat(corpo)
                .doesNotContain("\"senha\"")
                .doesNotContain("$2a$")
                .doesNotContain(SENHA_ALUNO)
                .doesNotContain(aluno.getCpf())
                .doesNotContain("observacoes")
                .doesNotContain("atestadoMedico");
    }

    @Test
    void perfilSemTokenEhNaoAutorizado() throws Exception {
        mockMvc.perform(get("/api/portal/perfil")).andExpect(status().isUnauthorized());
    }

    // ==================== Edição ====================

    @Test
    void editaNomeEmailETelefoneEPersisteNoBanco() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String novoEmail = emailUnico("perfil-editado");

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(tokenDe(aluno)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Aluno Nome Novo",
                                "email", novoEmail,
                                "telefone", "11912345678"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Aluno Nome Novo"))
                .andExpect(jsonPath("$.email").value(novoEmail))
                .andExpect(jsonPath("$.telefone").value("11912345678"));

        // Persistência real: reconsultando o banco, e não só a resposta.
        Aluno salvo = alunoRepository.findById(aluno.getId()).orElseThrow();
        assertThat(salvo.getNome()).isEqualTo("Aluno Nome Novo");
        assertThat(salvo.getEmail()).isEqualTo(novoEmail);
        assertThat(salvo.getTelefone()).isEqualTo("11912345678");

        // A senha e o CPF continuam intactos após a edição.
        assertThat(salvo.getCpf()).isEqualTo(aluno.getCpf());
        assertThat(passwordEncoder.matches(SENHA_ALUNO, salvo.getSenha())).isTrue();
    }

    @Test
    void edicaoContinuaVisivelEmNovaConsultaAApi() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Nome Persistido",
                                "email", emailUnico("perfil-persistido"),
                                "telefone", "11955556666"))))
                .andExpect(status().isOk());

        // Reabrindo a página de Perfil (nova chamada GET), os dados vêm salvos.
        mockMvc.perform(get("/api/portal/perfil").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome Persistido"))
                .andExpect(jsonPath("$.telefone").value("11955556666"));
    }

    @Test
    void camposInvalidosSaoRecusadosComMensagemPorCampo() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "  ", "email", "aluno@x.com", "telefone", "1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("nome"));

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Aluno", "email", "email-invalido", "telefone", "1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("email"));

        // Nada foi alterado no banco.
        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getNome())
                .isEqualTo("Aluno Perfil");
    }

    @Test
    void alunoNaoPodeUsarEmailDeAcessoDeOutroAlunoNemDaEquipe() throws Exception {
        Aluno alunoA = criarAlunoComSenha();
        Aluno alunoB = criarAlunoComSenha();
        Usuario admin = criarUsuario(emailUnico("perfil-admin"), "SenhaForte123", "ADMIN");

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(tokenDe(alunoA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Aluno", "email", alunoB.getEmail(), "telefone", "1"))))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(tokenDe(alunoA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Aluno", "email", admin.getEmail(), "telefone", "1"))))
                .andExpect(status().isConflict());
    }

    @Test
    void requisicaoForjadaNaoAlteraCargoNemDadosDeOutroAluno() throws Exception {
        Aluno alunoA = criarAlunoComSenha();
        Aluno alunoB = criarAlunoComSenha();
        String emailB = alunoB.getEmail();
        String emailNovo = emailUnico("perfil-proprio");

        // Mesmo enviando campos que não existem no DTO de entrada (id do OUTRO
        // aluno, "cargo", "situacao", "plano", "senha"), o backend aplica a
        // mudança apenas em quem está autenticado: o id vem do token e o serviço
        // só recebe os campos pessoais autorizados.
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(tokenDe(alunoA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "id", alunoB.getId(),
                                "nome", "Aluno Um",
                                "email", emailNovo,
                                "telefone", "11999998888",
                                "cargo", "ADMIN",
                                "status", "admin",
                                "situacao", "INATIVO",
                                "plano", "Anual",
                                "senha", "hackeada",
                                "observacoes", "não deve gravar"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alunoA.getId()))
                .andExpect(jsonPath("$.nome").value("Aluno Um"))
                .andExpect(jsonPath("$.plano").value("Mensal"))
                .andExpect(jsonPath("$.situacao").value("ATIVO"));

        // O cadastro do aluno B permanece intacto.
        Aluno b = alunoRepository.findById(alunoB.getId()).orElseThrow();
        assertThat(b.getNome()).isEqualTo("Aluno Perfil");
        assertThat(b.getEmail()).isEqualTo(emailB);
        assertThat(b.getTelefone()).isEqualTo("11987654321");
        assertThat(b.getSituacao()).isEqualTo("ATIVO");
        assertThat(b.getPlano()).isEqualTo("Mensal");
        assertThat(b.getCpf()).isEqualTo(alunoB.getCpf());

        // E o aluno A continua ALUNO (o cargo não veio do cliente em lugar algum),
        // com o plano e a situação originais e a senha de acesso intacta.
        Aluno a = alunoRepository.findById(alunoA.getId()).orElseThrow();
        assertThat(a.getNome()).isEqualTo("Aluno Um");
        assertThat(a.getEmail()).isEqualTo(emailNovo);
        assertThat(a.getPlano()).isEqualTo("Mensal");
        assertThat(a.getSituacao()).isEqualTo("ATIVO");
        assertThat(passwordEncoder.matches(SENHA_ALUNO, a.getSenha())).isTrue();
    }

    @Test
    void edicaoDePerfilSemTokenEhNaoAutorizada() throws Exception {
        mockMvc.perform(put("/api/portal/perfil")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "X", "email", "x@y.com", "telefone", "1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeEquipeNaoUtilizaEndpointDeAluno() throws Exception {
        Usuario admin = criarUsuario(emailUnico("perfil-equipe"), "SenhaForte123", "ADMIN");

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Intruso", "email", "intruso@x.com",
                                "telefone", "11900000000"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void telefoneFormatadoEhPreservadoComoEsta() throws Exception {
        Aluno aluno = criarAlunoComSenha();

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(tokenDe(aluno)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Aluno Perfil",
                                "email", aluno.getEmail(),
                                "telefone", "(11) 98765-4321"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value("(11) 98765-4321"));

        // Compatibilidade: o valor é apenas aparado, sem reformatar dados já
        // existentes (o banco convive com "11987654321" e com números formatados).
        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getTelefone())
                .isEqualTo("(11) 98765-4321");
    }

    @Test
    void trocaDeEmailNaoDerrubaASessaoEOPassadoViraONovoLogin() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);
        String emailNovo = emailUnico("perfil-login");

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", aluno.getNome(), "email", emailNovo,
                                "telefone", aluno.getTelefone()))))
                .andExpect(status().isOk());

        // A sessão atual continua válida: o token carrega apenas o id do aluno,
        // nunca o e-mail — não há logout automático após a troca.
        mockMvc.perform(get("/api/portal/perfil").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(emailNovo));

        // O novo e-mail já é a identidade de login; o antigo deixa de valer.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailNovo, "senha", SENHA_ALUNO))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", aluno.getEmail(), "senha", SENHA_ALUNO))))
                .andExpect(status().isUnauthorized());
    }

    // ==================== CPF ====================

    @Test
    void editaCpfComEMascaraESemMascaraEGravaSomenteDigitos() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);
        String cpfNovo = cpfValidoAleatorio();

        // Enviado com máscara...
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", aluno.getNome(), "email", aluno.getEmail(),
                                "telefone", aluno.getTelefone(),
                                "cpf", com.belval.academia.util.Cpf.formatar(cpfNovo)))))
                .andExpect(status().isOk())
                // A resposta segue mascarada: o CPF completo nunca volta ao app.
                .andExpect(jsonPath("$.cpfMascarado").value("***.***-" + cpfNovo.substring(9)));
        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getCpf())
                .isEqualTo(cpfNovo);

        // ...e depois sem máscara: o valor gravado é sempre só dígitos.
        String cpfSemMascara = cpfValidoAleatorio();
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", aluno.getNome(), "email", aluno.getEmail(),
                                "telefone", aluno.getTelefone(),
                                "cpf", cpfSemMascara))))
                .andExpect(status().isOk());
        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getCpf())
                .isEqualTo(cpfSemMascara);
    }


    @Test
    void cpfInvalidoEhRejeitadoENadaMuda() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        // CPF conhecido com dígitos verificadores errados.
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", aluno.getNome(), "email", aluno.getEmail(),
                                "telefone", aluno.getTelefone(),
                                "cpf", "123.456.789-00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("cpf"));

        // Sequência repetida (passa no cálculo, mas não é CPF real).
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", aluno.getNome(), "email", aluno.getEmail(),
                                "telefone", aluno.getTelefone(),
                                "cpf", "111.111.111-11"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("cpf"));

        // Tamanho errado / não numérico.
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", aluno.getNome(), "email", aluno.getEmail(),
                                "telefone", aluno.getTelefone(),
                                "cpf", "abc"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("cpf"));

        // O CPF original permanece no banco.
        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getCpf())
                .isEqualTo(aluno.getCpf());
    }

    @Test
    void alunoNaoConsegueAssumirCpfDeOutroAluno() throws Exception {
        Aluno alunoA = criarAlunoComSenha();
        Aluno alunoB = criarAlunoComSenha();

        // Enviando inclusive o id do aluno B no corpo: a identidade vem do token,
        // então a tentativa vale para A e é recusada por duplicidade.
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(tokenDe(alunoA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "id", alunoB.getId(),
                                "nome", "Aluno Um",
                                "email", emailUnico("perfil-cpf"),
                                "telefone", "11911112222",
                                "cpf", alunoB.getCpf()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("cpf"));

        // Ninguém mudou de CPF.
        assertThat(alunoRepository.findById(alunoA.getId()).orElseThrow().getCpf())
                .isEqualTo(alunoA.getCpf());
        assertThat(alunoRepository.findById(alunoB.getId()).orElseThrow().getCpf())
                .isEqualTo(alunoB.getCpf());
    }

    @Test
    void cpfEmBrancoOuAusenteMantemOCpfAtual() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        // Sem a chave no corpo (envio parcial)...
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Aluno Perfil",
                                "email", aluno.getEmail(),
                                "telefone", "11987654321"))))
                .andExpect(status().isOk());

        // ...e com a chave vazia: em nenhum caso o CPF gravado é apagado.
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Aluno Perfil",
                                "email", aluno.getEmail(),
                                "telefone", "11987654321",
                                "cpf", "   "))))
                .andExpect(status().isOk());

        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getCpf())
                .isEqualTo(aluno.getCpf());
    }

    // ==================== Data de nascimento ====================

    @Test
    void editaNascimentoValidoEPersisteNoBanco() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", aluno.getNome(), "email", aluno.getEmail(),
                                "telefone", aluno.getTelefone(),
                                "dataNascimento", "1995-11-30"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataNascimento").value("1995-11-30"));

        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getDataNascimento())
                .isEqualTo(LocalDate.of(1995, 11, 30));
    }

    @Test
    void nascimentoFuturoEhRejeitado() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", aluno.getNome(), "email", aluno.getEmail(),
                                "telefone", aluno.getTelefone(),
                                "dataNascimento", LocalDate.now().plusDays(1).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("dataNascimento"));

        // A data original continua intacta.
        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getDataNascimento())
                .isEqualTo(LocalDate.of(1990, 5, 20));
    }

    @Test
    void nascimentoInvalidoEhRejeitado() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        // Formato diferente do ISO esperado pela API.
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", aluno.getNome(), "email", aluno.getEmail(),
                                "telefone", aluno.getTelefone(),
                                "dataNascimento", "31/12/1990"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("dataNascimento"));

        // Calendário impossível (mês inexistente).
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", aluno.getNome(), "email", aluno.getEmail(),
                                "telefone", aluno.getTelefone(),
                                "dataNascimento", "1990-13-45"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("dataNascimento"));

        // Conteúdo não numérico.
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", aluno.getNome(), "email", aluno.getEmail(),
                                "telefone", aluno.getTelefone(),
                                "dataNascimento", "ontem"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("dataNascimento"));

        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getDataNascimento())
                .isEqualTo(LocalDate.of(1990, 5, 20));
    }

    @Test
    void nascimentoEmBrancoOuAusenteMantemADataAtual() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", aluno.getNome(), "email", aluno.getEmail(),
                                "telefone", aluno.getTelefone(),
                                "dataNascimento", ""))))
                .andExpect(status().isOk());

        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getDataNascimento())
                .isEqualTo(LocalDate.of(1990, 5, 20));
    }

    // ==================== Foto de perfil ====================

    @Test
    void alunoSemFotoVeOCampoVazioNaInterface() throws Exception {
        Aluno aluno = criarAlunoComSenha();

        mockMvc.perform(get("/api/portal/perfil").header("Authorization", bearer(tokenDe(aluno))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoPerfilBase64").doesNotExist());
    }

    @Test
    void salvaNovaFotoEElaContinuaDisponivelAoReabrirOPerfil() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        mockMvc.perform(put("/api/portal/perfil/foto")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fotoBase64", FOTO_PNG))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoPerfilBase64").value(FOTO_PNG));

        // Gravada no banco de verdade (e não só no estado do aplicativo).
        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getFotoPerfilBase64())
                .isEqualTo(FOTO_PNG);

        // Reabrindo o perfil (nova requisição, como ao reiniciar o app).
        mockMvc.perform(get("/api/portal/perfil").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoPerfilBase64").value(FOTO_PNG));

        // E em uma nova sessão (login feito de novo).
        mockMvc.perform(get("/api/portal/perfil").header("Authorization", bearer(tokenDe(aluno))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoPerfilBase64").value(FOTO_PNG));
    }

    @Test
    void formatosNaoPermitidosEAImagensGrandesSaoRecusados() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        // Base64 que não é imagem (ex.: um PDF enviado no lugar da foto).
        mockMvc.perform(put("/api/portal/perfil/foto")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fotoBase64", "data:application/pdf;base64,JVBERi0xLjQK"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("foto"));

        // Texto solto, sem data URL.
        mockMvc.perform(put("/api/portal/perfil/foto")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fotoBase64", "não sou uma imagem"))))
                .andExpect(status().isBadRequest());

        // Acima do limite configurado (app.upload.foto-max-bytes).
        mockMvc.perform(put("/api/portal/perfil/foto")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fotoBase64",
                                "data:image/jpeg;base64," + "A".repeat(1_100_000)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("foto"));

        // Nenhuma foto foi gravada em nenhum dos casos.
        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getFotoPerfilBase64())
                .isNull();
    }

    @Test
    void fotoPodeSerRemovidaEnviandoValorVazio() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        mockMvc.perform(put("/api/portal/perfil/foto")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fotoBase64", FOTO_PNG))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/portal/perfil/foto")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fotoBase64", ""))))
                .andExpect(status().isOk());

        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getFotoPerfilBase64())
                .isNull();
    }

    @Test
    void alunoSoAlteraAPropriaFoto() throws Exception {
        Aluno alunoA = criarAlunoComSenha();
        Aluno alunoB = criarAlunoComSenha();

        mockMvc.perform(put("/api/portal/perfil/foto")
                        .header("Authorization", bearer(tokenDe(alunoA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fotoBase64", FOTO_PNG))))
                .andExpect(status().isOk());

        // A foto do aluno B continua vazia: o id vem do token, não do corpo.
        assertThat(alunoRepository.findById(alunoB.getId()).orElseThrow().getFotoPerfilBase64())
                .isNull();
    }

    @Test
    void fotoSemTokenEhNaoAutorizada() throws Exception {
        mockMvc.perform(put("/api/portal/perfil/foto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fotoBase64", FOTO_PNG))))
                .andExpect(status().isUnauthorized());
    }
}