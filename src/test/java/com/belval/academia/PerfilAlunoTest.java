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
 * Página de PERFIL do aluno: leitura, edição direta SOMENTE de contato,
 * endereço e contato de emergência (PUT /api/portal/perfil) e foto de perfil
 * com persistência real no banco.
 *
 * <p>Cobre os cenários exigidos: carregar o perfil (inclusive campos novos de
 * endereço/emergência), editar e salvar, confirmar persistência em nova
 * consulta, campos protegidos (nome, CPF, nascimento, sexo) ignorados mesmo
 * quando enviados, validação de campos (e-mail, CEP, telefone, limites),
 * duplicidade de e-mail, campos omitidos preservados, isolamento entre alunos
 * (sem edição de perfil alheio nem alteração de campos administrativos),
 * manter sessão/login após troca de e-mail, além da persistência da foto
 * (selecionar, cancelar, salvar, reabrir, formato inválido e tamanho).</p>
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
        aluno.setSexo("M");
        aluno.setEmail(emailUnico("perfil"));
        // Telefone único por teste: o índice ux_aluno_telefone (schema.sql)
        // impediria dois alunos com o mesmo número no banco compartilhado.
        aluno.setTelefone(telefoneUnico());
        aluno.setCep("01310100");
        aluno.setLogradouro("Av. Paulista");
        aluno.setNumero("1000");
        aluno.setComplemento("Apto 111");
        aluno.setBairro("Bela Vista");
        aluno.setContatoEmergenciaNome("Mae do Aluno");
        aluno.setContatoEmergenciaTelefone("11911112222");
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
                .andExpect(jsonPath("$.telefone").value(aluno.getTelefone()))
                .andExpect(jsonPath("$.plano").value("Mensal"))
                .andExpect(jsonPath("$.situacao").value("ATIVO"))
                .andExpect(jsonPath("$.dataNascimento").value("1990-05-20"))
                .andExpect(jsonPath("$.sexo").value("M"))
                .andExpect(jsonPath("$.cep").value("01310100"))
                .andExpect(jsonPath("$.logradouro").value("Av. Paulista"))
                .andExpect(jsonPath("$.numero").value("1000"))
                .andExpect(jsonPath("$.complemento").value("Apto 111"))
                .andExpect(jsonPath("$.bairro").value("Bela Vista"))
                .andExpect(jsonPath("$.contatoEmergenciaNome").value("Mae do Aluno"))
                .andExpect(jsonPath("$.contatoEmergenciaTelefone").value("11911112222"))
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
    void editaContatoEnderecoEEmergenciaEPersisteNoBanco() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String novoEmail = emailUnico("perfil-editado");

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(tokenDe(aluno)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", novoEmail,
                                "telefone", "11912345678",
                                "cep", "04538133",
                                "logradouro", "Rua Doutor Renato Paes de Barros",
                                "numero", "714",
                                "complemento", "Torre 2",
                                "bairro", "Itaim Bibi",
                                "contatoEmergenciaNome", "Pai do Aluno",
                                "contatoEmergenciaTelefone", "11933334444"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(novoEmail))
                .andExpect(jsonPath("$.telefone").value("11912345678"))
                .andExpect(jsonPath("$.cep").value("04538133"))
                .andExpect(jsonPath("$.logradouro").value("Rua Doutor Renato Paes de Barros"))
                .andExpect(jsonPath("$.numero").value("714"))
                .andExpect(jsonPath("$.complemento").value("Torre 2"))
                .andExpect(jsonPath("$.bairro").value("Itaim Bibi"))
                .andExpect(jsonPath("$.contatoEmergenciaNome").value("Pai do Aluno"))
                .andExpect(jsonPath("$.contatoEmergenciaTelefone").value("11933334444"));

        // Persistência real: reconsultando o banco, e não só a resposta.
        Aluno salvo = alunoRepository.findById(aluno.getId()).orElseThrow();
        assertThat(salvo.getEmail()).isEqualTo(novoEmail);
        assertThat(salvo.getTelefone()).isEqualTo("11912345678");
        assertThat(salvo.getCep()).isEqualTo("04538133");
        assertThat(salvo.getLogradouro()).isEqualTo("Rua Doutor Renato Paes de Barros");
        assertThat(salvo.getNumero()).isEqualTo("714");
        assertThat(salvo.getComplemento()).isEqualTo("Torre 2");
        assertThat(salvo.getBairro()).isEqualTo("Itaim Bibi");
        assertThat(salvo.getContatoEmergenciaNome()).isEqualTo("Pai do Aluno");
        assertThat(salvo.getContatoEmergenciaTelefone()).isEqualTo("11933334444");

        // Os dados protegidos e a senha continuam intactos após a edição.
        assertThat(salvo.getNome()).isEqualTo("Aluno Perfil");
        assertThat(salvo.getCpf()).isEqualTo(aluno.getCpf());
        assertThat(salvo.getDataNascimento()).isEqualTo(LocalDate.of(1990, 5, 20));
        assertThat(salvo.getSexo()).isEqualTo("M");
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
                                "email", emailUnico("perfil-persistido"),
                                "telefone", "11955556666",
                                "cep", "99999999",
                                "logradouro", "Rua das Laranjeiras",
                                "numero", "42"))))
                .andExpect(status().isOk());

        // Reabrindo a página de Perfil (nova chamada GET), os dados vêm salvos.
        mockMvc.perform(get("/api/portal/perfil").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value("11955556666"))
                .andExpect(jsonPath("$.cep").value("99999999"))
                .andExpect(jsonPath("$.logradouro").value("Rua das Laranjeiras"))
                .andExpect(jsonPath("$.numero").value("42"));
    }

    @Test
    void camposInvalidosSaoRecusadosComMensagemPorCampo() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        // CEP com menos de 8 dígitos.
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cep", "1234", "email", "aluno@x.com",
                                "telefone", "11987654321"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("cep"));

        // E-mail com formato inválido.
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cep", "01310100", "email", "email-invalido",
                                "telefone", "11987654321"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("email"));

        // Telefone com conteúdo que não é número/parênteses/travessão.
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cep", "01310100", "email", aluno.getEmail(),
                                "telefone", "abc"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("telefone"));

        // Nada foi alterado no banco.
        Aluno salvo = alunoRepository.findById(aluno.getId()).orElseThrow();
        assertThat(salvo.getNome()).isEqualTo("Aluno Perfil");
        assertThat(salvo.getCep()).isEqualTo("01310100");
        assertThat(salvo.getEmail()).isEqualTo(aluno.getEmail());
    }

    @Test
    void camposProtegidosSaoIgnoradosMesmoEnviados() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        // Nome, CPF, nascimento e sexo não existem no DTO: o Jackson os ignora
        // e nada muda — a correção desses campos é exclusiva da recepção.
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Nome Forjado",
                                "cpf", cpfValidoAleatorio(),
                                "dataNascimento", "2001-02-03",
                                "sexo", "F",
                                "email", aluno.getEmail(),
                                "telefone", "11977778888"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Aluno Perfil"))
                .andExpect(jsonPath("$.sexo").value("M"))
                .andExpect(jsonPath("$.dataNascimento").value("1990-05-20"))
                // O CPF volta sempre mascarado, com os dígitos originais.
                .andExpect(jsonPath("$.cpfMascarado")
                        .value("***.***-" + aluno.getCpf().substring(9)));

        Aluno salvo = alunoRepository.findById(aluno.getId()).orElseThrow();
        assertThat(salvo.getNome()).isEqualTo("Aluno Perfil");
        assertThat(salvo.getCpf()).isEqualTo(aluno.getCpf());
        assertThat(salvo.getDataNascimento()).isEqualTo(LocalDate.of(1990, 5, 20));
        assertThat(salvo.getSexo()).isEqualTo("M");
        // Somente o campo editável enviado foi aplicado.
        assertThat(salvo.getTelefone()).isEqualTo("11977778888");
    }

    @Test
    void camposDeTextoAcimaDoLimiteSaoRecusados() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cep", "01310100", "email", aluno.getEmail(),
                                "logradouro", "x".repeat(151)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("logradouro"));

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cep", "01310100", "email", aluno.getEmail(),
                                "contatoEmergenciaNome", "x".repeat(151)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("contatoEmergenciaNome"));

        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getLogradouro())
                .isEqualTo("Av. Paulista");
    }

    @Test
    void camposAusentesSaoPreservadosEVaziosLimpaOpicionais() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        // Envio parcial: só o e-mail. Todo o resto permanece intacto.
        String emailParcial = emailUnico("perfil-parcial");
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailParcial))))
                .andExpect(status().isOk());

        Aluno aposParcial = alunoRepository.findById(aluno.getId()).orElseThrow();
        assertThat(aposParcial.getEmail()).isEqualTo(emailParcial);
        assertThat(aposParcial.getTelefone()).isEqualTo(aluno.getTelefone());
        assertThat(aposParcial.getContatoEmergenciaNome()).isEqualTo("Mae do Aluno");

        // String vazia limpa um campo opcional (complemento)...
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailParcial, "complemento", "   "))))
                .andExpect(status().isOk());
        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getComplemento())
                .isNull();

        // ...mas e-mail vazio é recusado (obrigatório).
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("email"));
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
                // Nome é campo protegido: enviado ou não, mantém o valor do banco.
                .andExpect(jsonPath("$.nome").value("Aluno Perfil"))
                .andExpect(jsonPath("$.plano").value("Mensal"))
                .andExpect(jsonPath("$.situacao").value("ATIVO"));

        // O cadastro do aluno B permanece intacto.
        Aluno b = alunoRepository.findById(alunoB.getId()).orElseThrow();
        assertThat(b.getNome()).isEqualTo("Aluno Perfil");
        assertThat(b.getEmail()).isEqualTo(emailB);
        assertThat(b.getTelefone()).isEqualTo(alunoB.getTelefone());
        assertThat(b.getSituacao()).isEqualTo("ATIVO");
        assertThat(b.getPlano()).isEqualTo("Mensal");
        assertThat(b.getCpf()).isEqualTo(alunoB.getCpf());

        // E o aluno A continua ALUNO (o cargo não veio do cliente em lugar algum),
        // com o nome original, plano/situação e a senha de acesso intactos.
        Aluno a = alunoRepository.findById(alunoA.getId()).orElseThrow();
        assertThat(a.getNome()).isEqualTo("Aluno Perfil");
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
    void telefoneFormatadoEhNormalizadoParaSoDigitos() throws Exception {
        Aluno aluno = criarAlunoComSenha();

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(tokenDe(aluno)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Aluno Perfil",
                                "email", aluno.getEmail(),
                                "telefone", "(11) 98765-4321"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value("11987654321"));

        // Normalização para unicidade: a gravação usa só os dígitos (mesma regra
        // da migração V5 e do índice único no banco), então "(11) 98765-4321" e
        // "11987654321" são o MESMO telefone — a comparação não depende da
        // máscara enviada pelo formulário.
        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getTelefone())
                .isEqualTo("11987654321");
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

    // ==================== Campos protegidos: CPF, nascimento, sexo ====================

    @Test
    void cpfEnviadoComOuSemMascaraEhIgnorado() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);
        String cpfNovo = cpfValidoAleatorio();

        // Enviado com máscara...
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", aluno.getEmail(),
                                "cpf", com.belval.academia.util.Cpf.formatar(cpfNovo)))))
                .andExpect(status().isOk())
                // A resposta segue mascarada com os dígitos ORIGINAIS.
                .andExpect(jsonPath("$.cpfMascarado")
                        .value("***.***-" + aluno.getCpf().substring(9)));

        // ...e depois sem máscara: o campo não existe no DTO, nada muda.
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", aluno.getEmail(),
                                "cpf", cpfNovo))))
                .andExpect(status().isOk());

        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getCpf())
                .isEqualTo(aluno.getCpf());
    }

    @Test
    void cpfInvalidoEnviadoNaoGeraErroNemAlteraNada() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        // O campo está fora do DTO: nem validação roda, nem erro retorna —
        // o payload é simplesmente ignorado (inclusive o CPF de outro aluno).
        for (String cpfTentado : new String[] {"123.456.789-00", "111.111.111-11", "abc"}) {
            mockMvc.perform(put("/api/portal/perfil")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", aluno.getEmail(),
                                    "cpf", cpfTentado))))
                    .andExpect(status().isOk());
        }

        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getCpf())
                .isEqualTo(aluno.getCpf());
    }

    @Test
    void alunoNaoConsegueAssumirCpfDeOutroAluno() throws Exception {
        Aluno alunoA = criarAlunoComSenha();
        Aluno alunoB = criarAlunoComSenha();

        // Enviando inclusive o id e o CPF do aluno B no corpo: a identidade vem
        // do token e o CPF não existe no DTO — ninguém muda de CPF.
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(tokenDe(alunoA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "id", alunoB.getId(),
                                "email", emailUnico("perfil-cpf"),
                                "telefone", "11911112222",
                                "cpf", alunoB.getCpf()))))
                .andExpect(status().isOk());

        assertThat(alunoRepository.findById(alunoA.getId()).orElseThrow().getCpf())
                .isEqualTo(alunoA.getCpf());
        assertThat(alunoRepository.findById(alunoB.getId()).orElseThrow().getCpf())
                .isEqualTo(alunoB.getCpf());
    }

    // ==================== Data de nascimento e sexo ====================

    @Test
    void nascimentoValidoEnviadoEhIgnorado() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", aluno.getEmail(),
                                "dataNascimento", "1995-11-30"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataNascimento").value("1990-05-20"));

        assertThat(alunoRepository.findById(aluno.getId()).orElseThrow().getDataNascimento())
                .isEqualTo(LocalDate.of(1990, 5, 20));
    }

    @Test
    void nascimentoFuturoOuInvalidoEnviadoEhIgnorado() throws Exception {
        Aluno aluno = criarAlunoComSenha();
        String token = tokenDe(aluno);

        // Antes rejeitado com 400; agora o campo está fora do DTO e o payload
        // é ignorado sem erro — a data continua a cadastrada na recepção.
        for (String dataTentada : new String[] {
                LocalDate.now().plusDays(1).toString(), "31/12/1990", "1990-13-45", "ontem", ""}) {
            mockMvc.perform(put("/api/portal/perfil")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", aluno.getEmail(),
                                    "dataNascimento", dataTentada))))
                    .andExpect(status().isOk());
        }

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