package com.belval.academia;

import com.belval.academia.model.Usuario;
import com.belval.academia.service.AtivacaoAlunoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ativação por código (parte 4: auxílio da recepção a aluno que perdeu o código).
 *
 * <p>O código só existe no banco como hash BCrypt (com salt), logo NÃO pode ser
 * recuperado. O fluxo suportado é a <b>reemissão</b> via
 * {@code POST /api/aluno/{id}/codigo-ativacao}, que substitui o registro único
 * por aluno e invalida o código anterior. Estes testes cobrem: invalidação do
 * código antigo, emissões repetidas (só o último vale), autorização da rota e
 * ausência do código nas consultas de detalhe/listagem.</p>
 */
class AtivacaoContaRecuperacaoTest extends AtivacaoContaTest {

    /** Cadastra o aluno pela recepção e devolve o código recém-emitido. */
    private String cadastrarComCodigo(Usuario recepcao, String emailAluno) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(recepcao)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cadastro(emailAluno))))
                .andExpect(status().isOk())
                .andReturn();
        String codigo = extrairCodigo(resultado);
        assertThat(codigo).isNotNull();
        return codigo;
    }

    /** Id do aluno a partir do e-mail usado no cadastro. */
    private Long idDoAluno(String emailAluno) {
        return alunoRepository.findAll().stream()
                .filter(a -> emailAluno.equalsIgnoreCase(a.getEmail()))
                .findFirst().orElseThrow().getId();
    }

    /** Emite um código novo e devolve; falha se a API não devolver um código. */
    private String emitirCodigo(Usuario funcionario, Long alunoId) throws Exception {
        String corpo = mockMvc.perform(post("/api/aluno/" + alunoId + "/codigo-ativacao")
                        .header("Authorization", bearer(tokenDe(funcionario))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contaJaAtiva").value(false))
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String codigo = objectMapper.readTree(corpo).get("codigoAtivacao").asText();
        assertThat(codigo).hasSize(AtivacaoAlunoService.CODIGO_TAMANHO);
        // Nunca hash/credencial junto do código.
        assertThat(corpo).doesNotContain("$2a$");
        return codigo;
    }

    /** Tenta ativar a conta com o código informado (espera sucesso). */
    private void ativarCom(String emailAluno, String codigo, String senha) throws Exception {
        mockMvc.perform(post("/api/portal/ativar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "codigo", codigo,
                                "senha", senha, "confirmarSenha", senha))))
                .andExpect(status().isOk());
    }

    @Test
    void novoCodigoInvalidaOAnteriorEUsoAnteriorDeixaDeFuncionar() throws Exception {
        Usuario recepcao = criarUsuario(emailUnico("recepcao-rec"), "SenhaForte123", "RECEPCIONISTA");
        String emailAluno = emailUnico("aluno-perdeu");
        String codigoAntigo = cadastrarComCodigo(recepcao, emailAluno);
        Long alunoId = idDoAluno(emailAluno);

        String codigoNovo = emitirCodigo(recepcao, alunoId);
        assertThat(codigoNovo).isNotEqualTo(codigoAntigo);

        // Código anterior (perdido/expirado) não pode mais ser usado.
        mockMvc.perform(post("/api/portal/ativar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "codigo", codigoAntigo,
                                "senha", SENHA_NOVA, "confirmarSenha", SENHA_NOVA))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.aluno").doesNotExist());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "senha", SENHA_NOVA))))
                .andExpect(status().isUnauthorized());

        // Somente o código novo ativa a conta.
        ativarCom(emailAluno, codigoNovo, SENHA_NOVA);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "senha", SENHA_NOVA))))
                .andExpect(status().isOk());
    }

    @Test
    void emissaoRepetidaMantemSomenteUmCodigoValido() throws Exception {
        Usuario recepcao = criarUsuario(emailUnico("recepcao-rec2"), "SenhaForte123", "RECEPCIONISTA");
        String emailAluno = emailUnico("aluno-repetido");
        cadastrarComCodigo(recepcao, emailAluno);
        Long alunoId = idDoAluno(emailAluno);

        String primeiro = emitirCodigo(recepcao, alunoId);
        String segundo = emitirCodigo(recepcao, alunoId);
        String terceiro = emitirCodigo(recepcao, alunoId);

        // Um único registro por aluno (índice único) => um único código válido.
        assertThat(ativacaoRepository.findByAlunoId(alunoId)).isPresent();
        assertThat(segundo).isNotEqualTo(primeiro);
        assertThat(terceiro).isNotEqualTo(segundo);
        assertThat(terceiro).isNotEqualTo(primeiro);

        // Os dois códigos anteriores são rejeitados.
        mockMvc.perform(post("/api/portal/ativar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "codigo", primeiro,
                                "senha", SENHA_NOVA, "confirmarSenha", SENHA_NOVA))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/portal/ativar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "codigo", segundo,
                                "senha", SENHA_NOVA, "confirmarSenha", SENHA_NOVA))))
                .andExpect(status().isBadRequest());

        // Apenas o último código emitido funciona.
        ativarCom(emailAluno, terceiro, SENHA_NOVA);
    }

    @Test
    void quemNaoEhAdminOuRecepcaoNaoEmiteCodigo() throws Exception {
        Usuario recepcao = criarUsuario(emailUnico("recepcao-rec3"), "SenhaForte123", "RECEPCIONISTA");
        Usuario financeiro = criarUsuario(emailUnico("financeiro-rec"), "SenhaForte123", "FINANCEIRO");
        String emailAluno = emailUnico("aluno-protegido");
        String codigoOriginal = cadastrarComCodigo(recepcao, emailAluno);
        Long alunoId = idDoAluno(emailAluno);
        String rota = "/api/aluno/" + alunoId + "/codigo-ativacao";

        // Sem token -> 401 (entry point).
        mockMvc.perform(post(rota)).andExpect(status().isUnauthorized());
        // Cargo sem permissão -> 403 (access denied handler), sem emitir código.
        mockMvc.perform(post(rota).header("Authorization", bearer(tokenDe(financeiro))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigoAtivacao").doesNotExist());

        // Como nada foi emitido, o código original continua válido e funciona.
        ativarCom(emailAluno, codigoOriginal, SENHA_NOVA);
    }

    @Test
    void detalhesEListagemDoAlunoNaoExpoemCodigoNemHash() throws Exception {
        Usuario recepcao = criarUsuario(emailUnico("recepcao-rec4"), "SenhaForte123", "RECEPCIONISTA");
        String emailAluno = emailUnico("aluno-sigiloso");
        String codigo = cadastrarComCodigo(recepcao, emailAluno);
        Long alunoId = idDoAluno(emailAluno);
        String token = bearer(tokenDe(recepcao));

        String detalhe = mockMvc.perform(get("/api/aluno/" + alunoId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.codigoAtivacao").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String lista = mockMvc.perform(get("/api/aluno")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String corpo : new String[]{detalhe, lista}) {
            assertThat(corpo).doesNotContain(codigo);
            assertThat(corpo).doesNotContain("$2a$");
            assertThat(corpo).doesNotContain("codigoHash");
        }
    }
}
