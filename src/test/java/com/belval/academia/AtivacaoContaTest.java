package com.belval.academia;

import com.belval.academia.model.Aluno;
import com.belval.academia.model.AtivacaoAluno;
import com.belval.academia.model.Usuario;
import com.belval.academia.repository.AlunoRepository;
import com.belval.academia.repository.AtivacaoAlunoRepository;
import com.belval.academia.service.AtivacaoAlunoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ativação da conta mobile por código de uso único (parte 1: cadastro gera o
 * código; código válido define a senha sem duplicar o aluno; login funciona).
 */
class AtivacaoContaTest extends TesteBase {

    @Autowired
    protected AlunoRepository alunoRepository;

    @Autowired
    protected AtivacaoAlunoRepository ativacaoRepository;

    @Autowired
    protected AtivacaoAlunoService ativacaoService;

    protected static final String SENHA_NOVA = "MinhaSenha123";

    protected Map<String, String> cadastro(String email) {
        return Map.of(
                "nome", "Aluno Ativacao",
                "cpf", cpfValidoAleatorio(),
                "dataNascimento", "1990-01-01",
                "email", email,
                "plano", "Mensal");
    }

    protected String extrairCodigo(MvcResult resultado) {
        return resultado.getResponse().getHeader("X-Codigo-Ativacao");
    }

    private MvcResult cadastrar(Usuario recepcao, String emailAluno) throws Exception {
        return mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(recepcao)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cadastro(emailAluno))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andReturn();
    }

    @Test
    void cadastroPelaRecepcaoGeraCodigoUmaUnicaVez() throws Exception {
        Usuario recepcao = criarUsuario(emailUnico("recepcao"), "SenhaForte123", "RECEPCIONISTA");
        String emailAluno = emailUnico("aluno-codigo");

        MvcResult resultado = cadastrar(recepcao, emailAluno);
        String codigo = extrairCodigo(resultado);
        assertThat(codigo).isNotNull();
        assertThat(codigo).hasSize(AtivacaoAlunoService.CODIGO_TAMANHO);

        Aluno salvo = alunoRepository.findAll().stream()
                .filter(a -> emailAluno.equalsIgnoreCase(a.getEmail())).findFirst().orElseThrow();
        assertThat(salvo.getSenha()).isNull();
        AtivacaoAluno ativacao = ativacaoRepository.findByAlunoId(salvo.getId()).orElseThrow();
        assertThat(ativacao.getCodigoHash()).isNotEqualTo(codigo);
        assertThat(ativacao.getConsumidoEm()).isNull();
    }

    @Test
    void codigoValidoDefineSenhaSemDuplicarAlunoElogiDepois() throws Exception {
        Usuario recepcao = criarUsuario(emailUnico("recepcao2"), "SenhaForte123", "RECEPCIONISTA");
        String emailAluno = emailUnico("aluno-ativa");
        long totalAntes = alunoRepository.count();

        String codigo = extrairCodigo(cadastrar(recepcao, emailAluno));

        mockMvc.perform(post("/api/portal/ativar-conta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "codigo", codigo,
                                "senha", SENHA_NOVA, "confirmarSenha", SENHA_NOVA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andExpect(jsonPath("$.token").doesNotExist());

        assertThat(alunoRepository.count()).isEqualTo(totalAntes + 1);
        Aluno aluno = alunoRepository.localizarPorEmailDeAcesso(emailAluno).orElseThrow();
        assertThat(aluno.possuiSenhaDeAcesso()).isTrue();
        assertThat(aluno.getSenha()).isNotEqualTo(SENHA_NOVA);
        assertThat(aluno.getSenha()).startsWith("$2a$");
        assertThat(passwordEncoder.matches(SENHA_NOVA, aluno.getSenha())).isTrue();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", emailAluno, "senha", SENHA_NOVA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cargo").value("ALUNO"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
