package com.belval.academia;

import com.belval.academia.model.Aluno;
import com.belval.academia.model.Usuario;
import com.belval.academia.repository.AlunoRepository;
import com.belval.academia.repository.FuncionarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unicidade de TELEFONE: normalização (máscara, código do país 55), rejeição
 * de duplicidade em cadastro/edição, garantia DIRETA no índice único do banco
 * (sem passar pela validação da aplicação) e concorrência.
 *
 * <p>Escopo do modelo: telefone é único por tabela no banco
 * ({@code ux_aluno_telefone}, {@code ux_funcionario_telefone},
 * {@code ux_usuario_telefone} — schema.sql/V5) e único ENTRE cadastros pela
 * aplicação (UnicidadeCadastroService), exceto o par funcionário ↔ conta
 * vinculada (mesma identidade). O telefone de emergência do aluno fica fora
 * por ser de terceiros.</p>
 */
class TelefoneUnicidadeTest extends TesteBase {

    @Autowired
    private AlunoRepository alunoRepository;

    @Autowired
    private FuncionarioRepository funcionarioRepository;

    // ======================== Helpers ========================

    private Map<String, Object> alunoPayload(String nome, String cpf, String telefone) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", nome);
        corpo.put("cpf", cpf);
        corpo.put("email", emailUnico("tel-aluno"));
        corpo.put("telefone", telefone);
        return corpo;
    }

    private Map<String, Object> funcionarioPayload(String nome, String cpf, String telefone) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", nome);
        corpo.put("cargo", "TECNICO");
        corpo.put("dataAdmissao", "2024-03-01");
        corpo.put("cpf", cpf);
        corpo.put("telefone", telefone);
        return corpo;
    }

    private Map<String, Object> usuarioPayload(String telefone) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", "Pessoa da Equipe");
        corpo.put("email", emailUnico("tel-equipe"));
        corpo.put("senha", "SenhaForte123");
        corpo.put("cargo", "RECEPCIONISTA");
        corpo.put("telefone", telefone);
        return corpo;
    }

    private long criarAluno(Usuario admin, String nome, String cpf, String telefone) throws Exception {
        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(alunoPayload(nome, cpf, telefone))))
                .andExpect(status().isOk());
        return alunoRepository.findByCpf(cpf).orElseThrow().getId();
    }

    private long criarFuncionario(Usuario admin, String nome, String cpf, String telefone) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/funcionario")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(funcionarioPayload(nome, cpf, telefone))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
    }

    /** Mascara um telefone de 11 dígitos no formato do formulário: (DD) 99999-9999. */
    private static String comMascara(String digitos) {
        return "(" + digitos.substring(0, 2) + ") " + digitos.substring(2, 7) + "-" + digitos.substring(7);
    }

    /** Aluno criado direto no repositório, com senha de acesso (padrão PerfilAlunoTest). */
    private Aluno alunoComSenha(String senha, String telefone) {
        Aluno aluno = new Aluno();
        aluno.setNome("Aluno Telefone Portal");
        aluno.setCpf(cpfValidoAleatorio());
        aluno.setEmail(emailUnico("tel-portal"));
        aluno.setTelefone(telefone);
        aluno.setPlano("Mensal");
        aluno.setSituacao("ATIVO");
        aluno.setSenha(passwordEncoder.encode(senha));
        return alunoRepository.save(aluno);
    }

    private String tokenDoAluno(Aluno aluno, String senha) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", aluno.getEmail(), "senha", senha))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("token").asText();
    }

    // ======================== Cadastro ========================

    @Test
    void telefoneDuplicadoEntreAlunosEhRejeitado() throws Exception {
        Usuario admin = criarUsuario(emailUnico("tel-admin"), "SenhaForte123", "ADMIN");
        String telefone = telefoneUnico();
        criarAluno(admin, "AlunoTelefoneUm", cpfValidoAleatorio(), telefone);

        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(alunoPayload("AlunoTelefoneDois", cpfValidoAleatorio(), telefone))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("telefone"))
                .andExpect(jsonPath("$.erro").isNotEmpty());
    }

    @Test
    void telefoneComFormatacaoOuCodigoDoPaisEhReconhecidoComoDuplicado() throws Exception {
        Usuario admin = criarUsuario(emailUnico("tel-mask"), "SenhaForte123", "ADMIN");
        String telefone = telefoneUnico();
        criarAluno(admin, "AlunoMascara", cpfValidoAleatorio(), telefone);

        // Mesmo número com a máscara do formulário...
        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(alunoPayload("AlunoComMascara", cpfValidoAleatorio(), comMascara(telefone)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("telefone"));

        // ...e com código do país 55: normaliza para o mesmo número nacional.
        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(alunoPayload("AlunoComDDI", cpfValidoAleatorio(), "+55" + telefone))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("telefone"));
    }

    @Test
    void cadastroComTelefoneVazioViraNuloENaoConflita() throws Exception {
        Usuario admin = criarUsuario(emailUnico("tel-vazio"), "SenhaForte123", "ADMIN");

        // Vazio (JSON "") normaliza para NULL; vários NULL convivem no índice.
        long idUm = criarAluno(admin, "AlunoVazioUm", cpfValidoAleatorio(), "");
        long idDois = criarAluno(admin, "AlunoVazioDois", cpfValidoAleatorio(), "");

        assertThat(alunoRepository.findById(idUm).orElseThrow().getTelefone()).isNull();
        assertThat(alunoRepository.findById(idDois).orElseThrow().getTelefone()).isNull();
    }

    // ======================== Edição ========================

    @Test
    void edicaoDeAlunoParaTelefoneDeOutroAlunoEhRejeitada() throws Exception {
        Usuario admin = criarUsuario(emailUnico("tel-edit"), "SenhaForte123", "ADMIN");
        String cpfUm = cpfValidoAleatorio();
        String cpfDois = cpfValidoAleatorio();
        String telefoneUm = telefoneUnico();
        String telefoneDois = telefoneUnico();
        long idUm = criarAluno(admin, "AlunoEdicaoUm", cpfUm, telefoneUm);
        long idDois = criarAluno(admin, "AlunoEdicaoDois", cpfDois, telefoneDois);

        Map<String, Object> corpo = alunoPayload("AlunoEdicaoDois", cpfDois, telefoneUm);
        corpo.put("id", idDois);
        mockMvc.perform(put("/api/aluno/" + idDois)
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(corpo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("telefone"))
                .andExpect(jsonPath("$.erro").isNotEmpty());

        // A falha não altera nada: cada um continua com o próprio telefone.
        assertThat(alunoRepository.findById(idUm).orElseThrow().getTelefone()).isEqualTo(telefoneUm);
        assertThat(alunoRepository.findById(idDois).orElseThrow().getTelefone()).isEqualTo(telefoneDois);
    }

    @Test
    void alunoMantemProprioTelefoneNaEdicao() throws Exception {
        Usuario admin = criarUsuario(emailUnico("tel-proprio"), "SenhaForte123", "ADMIN");
        String cpf = cpfValidoAleatorio();
        String telefone = telefoneUnico();
        long id = criarAluno(admin, "AlunoProprioTelefone", cpf, telefone);

        // Enviando o MESMO número (até com máscara): não pode haver falso
        // conflito do registro consigo mesmo.
        Map<String, Object> corpo = alunoPayload("AlunoProprioTelefone", cpf, comMascara(telefone));
        corpo.put("id", id);
        mockMvc.perform(put("/api/aluno/" + id)
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(corpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value(telefone));

        assertThat(alunoRepository.findById(id).orElseThrow().getTelefone()).isEqualTo(telefone);
    }

    /** Aluno logado no app (portal): não troca o telefone por um de outro. */
    @Test
    void perfilDoAlunoNaoAceitaTelefoneDeOutroAlunoNemDaEquipe() throws Exception {
        String senha = "AlunoSenha123";
        Aluno alunoA = alunoComSenha(senha, telefoneUnico());
        Aluno alunoB = alunoComSenha(senha, telefoneUnico());
        Usuario equipe = criarUsuario(emailUnico("tel-equipe-dono"), "SenhaForte123", "RECEPCIONISTA");
        equipe.setTelefone(telefoneUnico());
        usuarioRepository.save(equipe);
        String token = tokenDoAluno(alunoA, senha);

        // Telefone do outro aluno...
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("telefone", alunoB.getTelefone()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("telefone"))
                .andExpect(jsonPath("$.erro").isNotEmpty());

        // ...e o de uma conta da equipe: ambos recusados.
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("telefone", equipe.getTelefone()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("telefone"));

        // Manter o próprio número continua liberado (sem falso conflito).
        mockMvc.perform(put("/api/portal/perfil")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("telefone", alunoA.getTelefone()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value(alunoA.getTelefone()));
    }

    // ======================== Funcionário ========================

    @Test
    void telefoneDuplicadoEntreFuncionariosEhRejeitado() throws Exception {
        Usuario admin = criarUsuario(emailUnico("tel-func"), "SenhaForte123", "ADMIN");
        String telefone = telefoneUnico();
        criarFuncionario(admin, "FuncionarioTelefoneUm", cpfValidoAleatorio(), telefone);

        mockMvc.perform(post("/api/funcionario")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(funcionarioPayload("FuncionarioTelefoneDois",
                                cpfValidoAleatorio(), telefone))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("telefone"))
                .andExpect(jsonPath("$.erro").isNotEmpty());
    }

    @Test
    void telefoneNaoCruzaEntreFuncionarioAlunoEUsuario() throws Exception {
        Usuario admin = criarUsuario(emailUnico("tel-cross"), "SenhaForte123", "ADMIN");

        // Funcionário -> aluno
        String telefoneFuncionario = telefoneUnico();
        criarFuncionario(admin, "FuncionarioCross", cpfValidoAleatorio(), telefoneFuncionario);
        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(alunoPayload("AlunoCrossUm", cpfValidoAleatorio(), telefoneFuncionario))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("telefone"));

        // Aluno -> funcionário
        String telefoneAluno = telefoneUnico();
        criarAluno(admin, "AlunoCross", cpfValidoAleatorio(), telefoneAluno);
        mockMvc.perform(post("/api/funcionario")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(funcionarioPayload("FuncionarioCrossDois",
                                cpfValidoAleatorio(), telefoneAluno))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("telefone"));

        // Usuário (conta da equipe) -> aluno e funcionário.
        String telefoneUsuario = telefoneUnico();
        mockMvc.perform(post("/api/usuario")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(usuarioPayload(telefoneUsuario))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(alunoPayload("AlunoCrossDois", cpfValidoAleatorio(), telefoneUsuario))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("telefone"));
        mockMvc.perform(post("/api/funcionario")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(funcionarioPayload("FuncionarioCrossTres",
                                cpfValidoAleatorio(), telefoneUsuario))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("telefone"));
    }

    @Test
    void edicaoDeFuncionarioParaTelefoneDeOutroRegistroEhRejeitada() throws Exception {
        Usuario admin = criarUsuario(emailUnico("tel-fedit"), "SenhaForte123", "ADMIN");
        String cpfUm = cpfValidoAleatorio();
        String cpfDois = cpfValidoAleatorio();
        String telefoneUm = telefoneUnico();
        String telefoneDois = telefoneUnico();
        long idUm = criarFuncionario(admin, "FuncionarioEdicaoUm", cpfUm, telefoneUm);
        long idDois = criarFuncionario(admin, "FuncionarioEdicaoDois", cpfDois, telefoneDois);

        Map<String, Object> corpo = funcionarioPayload("FuncionarioEdicaoDois", cpfDois, telefoneUm);
        mockMvc.perform(put("/api/funcionario/" + idDois)
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(corpo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("telefone"));

        assertThat(funcionarioRepository.findById(idUm).orElseThrow().getTelefone()).isEqualTo(telefoneUm);
        assertThat(funcionarioRepository.findById(idDois).orElseThrow().getTelefone()).isEqualTo(telefoneDois);
    }

    @Test
    void funcionarioComContaEspelhaTelefoneESemFalsoConflito() throws Exception {
        Usuario admin = criarUsuario(emailUnico("tel-conta"), "SenhaForte123", "ADMIN");
        String cpf = cpfValidoAleatorio();
        String telefone = telefoneUnico();
        String emailAcesso = emailUnico("tel-conta-acesso");

        Map<String, Object> corpo = funcionarioPayload("FuncionarioComConta", cpf, telefone);
        corpo.put("emailAcesso", emailAcesso);
        corpo.put("senha", "SenhaInicial123");
        corpo.put("confirmarSenha", "SenhaInicial123");
        MvcResult resultado = mockMvc.perform(post("/api/funcionario")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(corpo)))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();

        // A conta de acesso é a MESMA identidade: espelha o telefone do
        // funcionário (por isso o par repetido é permitido nas duas tabelas).
        Usuario conta = usuarioRepository.findByEmailIgnoreCase(emailAcesso).orElseThrow();
        assertThat(conta.getTelefone()).isEqualTo(telefone);

        // Editar MANTENDO o número não pode conflitar com a própria conta.
        Map<String, Object> edicao = funcionarioPayload("FuncionarioComConta Editado", cpf, comMascara(telefone));
        edicao.put("emailAcesso", emailAcesso);
        mockMvc.perform(put("/api/funcionario/" + id)
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(edicao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value(telefone));

        // Editar TROCANDO o número: liberado e a conta segue espelhada.
        String telefoneNovo = telefoneUnico();
        Map<String, Object> troca = funcionarioPayload("FuncionarioComConta Editado", cpf, telefoneNovo);
        troca.put("emailAcesso", emailAcesso);
        mockMvc.perform(put("/api/funcionario/" + id)
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(troca)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value(telefoneNovo));

        assertThat(funcionarioRepository.findById(id).orElseThrow().getTelefone()).isEqualTo(telefoneNovo);
        assertThat(usuarioRepository.findByEmailIgnoreCase(emailAcesso).orElseThrow().getTelefone())
                .isEqualTo(telefoneNovo);
    }

    // ======================== Conta da equipe ========================

    @Test
    void telefoneDuplicadoEntreUsuariosEhRejeitado() throws Exception {
        Usuario admin = criarUsuario(emailUnico("tel-user"), "SenhaForte123", "ADMIN");
        String telefone = telefoneUnico();

        mockMvc.perform(post("/api/usuario")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(usuarioPayload(telefone))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/usuario")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(usuarioPayload(telefone))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("telefone"))
                .andExpect(jsonPath("$.erro").isNotEmpty());
    }

    // ======================== Garantia na camada de persistência ========================

    @Test
    void persistenciaDiretaRejeitaTelefoneDuplicadoSemPassarPelaAplicacao() {
        String telefone = telefoneUnico();

        Aluno primeiro = new Aluno();
        primeiro.setNome("Aluno Persistencia Um");
        primeiro.setTelefone(telefone);
        alunoRepository.saveAndFlush(primeiro);

        Aluno segundo = new Aluno();
        segundo.setNome("Aluno Persistencia Dois");
        segundo.setTelefone(telefone);
        // Direto no repositório: nenhum controller/validação envolvido — só o
        // índice ux_aluno_telefone decide (proteção vale mesmo contornando
        // frontend e backend).
        assertThatThrownBy(() -> alunoRepository.saveAndFlush(segundo))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(alunoRepository.findAll().stream()
                .filter(a -> telefone.equals(a.getTelefone())).count()).isEqualTo(1);

        // O mesmo vale para a conta da equipe (ux_usuario_telefone).
        String telefoneConta = telefoneUnico();
        Usuario contaUm = new Usuario();
        contaUm.setNome("Conta Persistencia Um");
        contaUm.setEmail(emailUnico("persist"));
        contaUm.setSenha("$2a$10$hashdedemostracao");
        contaUm.setTelefone(telefoneConta);
        usuarioRepository.saveAndFlush(contaUm);

        Usuario contaDois = new Usuario();
        contaDois.setNome("Conta Persistencia Dois");
        contaDois.setEmail(emailUnico("persist"));
        contaDois.setSenha("$2a$10$hashdedemostracao");
        contaDois.setTelefone(telefoneConta);
        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(contaDois))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistenciaDiretaPermiteTelefonesNulos() {
        Aluno um = new Aluno();
        um.setNome("Aluno Nulo Um");
        Aluno dois = new Aluno();
        dois.setNome("Aluno Nulo Dois");

        alunoRepository.saveAndFlush(um);
        alunoRepository.saveAndFlush(dois);

        assertThat(um.getId()).isNotNull();
        assertThat(dois.getId()).isNotNull();
        assertThat(um.getTelefone()).isNull();
        assertThat(dois.getTelefone()).isNull();
    }

    // ======================== Concorrência ========================

    @Test
    void cadastrosSimultaneosComOMesmoTelefoneNaoGeramDuplicidade() throws Exception {
        Usuario admin = criarUsuario(emailUnico("tel-conc"), "SenhaForte123", "ADMIN");
        String token = tokenDe(admin);
        String telefone = telefoneUnico();
        int quantidade = 4;

        ExecutorService pool = Executors.newFixedThreadPool(quantidade);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            final int indice = i;
            futuros.add(pool.submit(() -> {
                largada.await();
                return mockMvc.perform(post("/api/aluno")
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(alunoPayload("Aluno Telefone Concorrente " + indice,
                                        cpfValidoAleatorio(), telefone))))
                        .andReturn().getResponse().getStatus();
            }));
        }
        largada.countDown();

        List<Integer> statusObtidos = new ArrayList<>();
        for (Future<Integer> futuro : futuros) {
            statusObtidos.add(futuro.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        // Quem passou pela validação ao mesmo tempo perde no índice único do
        // banco (409) — nunca persistem dois cadastros com o mesmo número.
        assertThat(statusObtidos).containsOnly(200, 409);
        assertThat(statusObtidos.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(alunoRepository.findAll().stream()
                .filter(a -> telefone.equals(a.getTelefone())).count()).isEqualTo(1);
    }
}
