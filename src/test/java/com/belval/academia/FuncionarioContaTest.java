package com.belval.academia;

import com.belval.academia.dto.FuncionarioResponseDTO;
import com.belval.academia.model.Funcionario;
import com.belval.academia.model.Usuario;
import com.belval.academia.repository.FuncionarioRepository;
import com.belval.academia.service.AutenticacaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Conta de acesso do funcionário: hash da senha, preservação da senha em
 * atualizações cadastrais e bloqueio de privilégio administrativo indevido.
 */
class FuncionarioContaTest extends TesteBase {

    @Autowired
    private FuncionarioRepository funcionarioRepository;

    private Map<String, Object> payload(String nome, String cargo, String emailAcesso) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", nome);
        corpo.put("cargo", cargo);
        corpo.put("dataAdmissao", "2024-02-01");
        corpo.put("cpf", cpfValidoAleatorio());
        corpo.put("emailAcesso", emailAcesso);
        return corpo;
    }

    private Long criarFuncionarioComConta(Usuario admin, Map<String, Object> corpo) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/funcionario")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(corpo)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void cadastroDeFuncionarioCriaContaComHashBcrypt() throws Exception {
        Usuario admin = criarUsuario(emailUnico("f-admin"), "SenhaForte123", "ADMIN");
        String emailAcesso = emailUnico("acesso-func");

        Map<String, Object> corpo = payload("Maria Colaboradora", "TECNICO", emailAcesso);
        corpo.put("senha", "SenhaInicial123");
        corpo.put("confirmarSenha", "SenhaInicial123");

        MvcResult resultado = mockMvc.perform(post("/api/funcionario")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(corpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailAcesso").value(emailAcesso))
                .andExpect(jsonPath("$.situacaoAcesso").value("ATIVA"))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain("SenhaInicial123")
                .doesNotContain("\"senha\"")
                .doesNotContain("$2a$");

        Usuario conta = usuarioRepository.findByEmailIgnoreCase(emailAcesso).orElseThrow();
        assertThat(AutenticacaoService.ehHashBcrypt(conta.getSenha())).isTrue();
        assertThat(passwordEncoder.matches("SenhaInicial123", conta.getSenha())).isTrue();

        // A conta criada realmente entra no sistema.
        tokenPorLogin(emailAcesso, "SenhaInicial123");
    }

    @Test
    void atualizacaoCadastralSemSenhaPreservaASenhaAtual() throws Exception {
        Usuario admin = criarUsuario(emailUnico("f2-admin"), "SenhaForte123", "ADMIN");
        String emailAcesso = emailUnico("acesso-func2");
        Map<String, Object> corpo = payload("João Colaborador", "RECEPCIONISTA", emailAcesso);
        corpo.put("senha", "SenhaInicial123");
        corpo.put("confirmarSenha", "SenhaInicial123");
        Long id = criarFuncionarioComConta(admin, corpo);

        String hashAntes = usuarioRepository.findByEmailIgnoreCase(emailAcesso).orElseThrow().getSenha();

        // Atualização cadastral SEM o campo senha (como o frontend envia).
        Map<String, Object> edicao = payload("João Colaborador Silva", "RECEPCIONISTA", emailAcesso);
        edicao.put("cpf", corpo.get("cpf"));
        edicao.put("senha", null);
        mockMvc.perform(put("/api/funcionario/" + id)
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(edicao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("João Colaborador Silva"));

        String hashDepois = usuarioRepository.findByEmailIgnoreCase(emailAcesso).orElseThrow().getSenha();
        assertThat(hashDepois).isEqualTo(hashAntes);
        tokenPorLogin(emailAcesso, "SenhaInicial123"); // continua entrando
    }

    @Test
    void alteracaoDeSenhaSubstituiOHashEInvalidaASenhaAntiga() throws Exception {
        Usuario admin = criarUsuario(emailUnico("f3-admin"), "SenhaForte123", "ADMIN");
        String emailAcesso = emailUnico("acesso-func3");
        Map<String, Object> corpo = payload("Ana Colaboradora", "FINANCEIRO", emailAcesso);
        corpo.put("senha", "SenhaInicial123");
        corpo.put("confirmarSenha", "SenhaInicial123");
        Long id = criarFuncionarioComConta(admin, corpo);

        String hashAntes = usuarioRepository.findByEmailIgnoreCase(emailAcesso).orElseThrow().getSenha();

        Map<String, Object> edicao = payload("Ana Colaboradora", "FINANCEIRO", emailAcesso);
        edicao.put("cpf", corpo.get("cpf"));
        edicao.put("senha", "NovaSenha456");
        edicao.put("confirmarSenha", "NovaSenha456");
        mockMvc.perform(put("/api/funcionario/" + id)
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(edicao)))
                .andExpect(status().isOk());

        String hashDepois = usuarioRepository.findByEmailIgnoreCase(emailAcesso).orElseThrow().getSenha();
        assertThat(hashDepois).isNotEqualTo(hashAntes);
        assertThat(AutenticacaoService.ehHashBcrypt(hashDepois)).isTrue();

        tokenPorLogin(emailAcesso, "NovaSenha456");

        mockMvc.perform(post("/api/usuario/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAcesso, "senha", "SenhaInicial123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void financeiroNaoPodeCriarContaComPerfilAdmin() throws Exception {
        Usuario financeiro = criarUsuario(emailUnico("f4-fin"), "SenhaForte123", "FINANCEIRO");

        Map<String, Object> corpo = payload("Falso Admin", "ADMIN", emailUnico("falso-admin"));
        corpo.put("senha", "SenhaInicial123");
        corpo.put("confirmarSenha", "SenhaInicial123");

        mockMvc.perform(post("/api/funcionario")
                        .header("Authorization", bearer(tokenDe(financeiro)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(corpo)))
                .andExpect(status().isForbidden());

        assertThat(usuarioRepository.findByEmailIgnoreCase((String) corpo.get("emailAcesso"))).isEmpty();
    }

    @Test
    void listagemDeFuncionariosNaoExpoeSenhaNemHash() throws Exception {
        Usuario admin = criarUsuario(emailUnico("f5-admin"), "SenhaForte123", "ADMIN");
        String emailAcesso = emailUnico("acesso-func5");
        Map<String, Object> corpo = payload("Carlos Colaborador", "TECNICO", emailAcesso);
        corpo.put("senha", "SenhaInicial123");
        corpo.put("confirmarSenha", "SenhaInicial123");
        criarFuncionarioComConta(admin, corpo);

        MvcResult resultado = mockMvc.perform(get("/api/funcionario")
                        .header("Authorization", bearer(tokenDe(admin))))
                .andExpect(status().isOk())
                .andReturn();

        String corpoResposta = resultado.getResponse().getContentAsString();
        assertThat(corpoResposta)
                .doesNotContain("\"senha\"")
                .doesNotContain("$2a$")
                .doesNotContain("$2b$")
                .contains(emailAcesso);
    }

    @Test
    void excluirFuncionarioRemoveAContaDeAcesso() throws Exception {
        Usuario admin = criarUsuario(emailUnico("f6-admin"), "SenhaForte123", "ADMIN");
        String emailAcesso = emailUnico("acesso-func6");
        Map<String, Object> corpo = payload("Pedro Colaborador", "TECNICO", emailAcesso);
        corpo.put("senha", "SenhaInicial123");
        corpo.put("confirmarSenha", "SenhaInicial123");
        Long id = criarFuncionarioComConta(admin, corpo);

        Funcionario funcionario = funcionarioRepository.findById(id).orElseThrow();
        Long usuarioId = funcionario.getUsuarioId();
        assertThat(usuarioId).isNotNull();

        mockMvc.perform(delete("/api/funcionario/" + id)
                        .header("Authorization", bearer(tokenDe(admin))))
                .andExpect(status().isOk());

        assertThat(funcionarioRepository.findById(id)).isEmpty();
        assertThat(usuarioRepository.findById(usuarioId)).isEmpty();
    }

    /** Garantia estrutural: o DTO de resposta não possui campo de senha. */
    @Test
    void dtoDeFuncionarioNaoTemCampoSenha() {
        assertThat(java.util.Arrays.stream(FuncionarioResponseDTO.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .noneMatch(nome -> nome.toLowerCase().contains("senha")))
                .isTrue();
    }
}
