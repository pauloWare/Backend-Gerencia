package com.belval.academia;

import com.belval.academia.model.Aluno;
import com.belval.academia.model.Usuario;
import com.belval.academia.repository.AlunoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** E-mail e CPF: normalização, validação, duplicidade e concorrência. */
class UnicidadeTest extends TesteBase {

    /** CPF com dígito verificador errado. */
    private static final String CPF_INVALIDO = "12345678900";

    @Autowired
    private AlunoRepository alunoRepository;

    private Map<String, Object> alunoPayload(String nome, String cpf, String email) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", nome);
        corpo.put("cpf", cpf);
        corpo.put("email", email);
        return corpo;
    }

    @Test
    void alunoComCpfEDadosNovosEhCriado() throws Exception {
        Usuario admin = criarUsuario(emailUnico("uni-admin"), "SenhaForte123", "ADMIN");
        String cpf = cpfValidoAleatorio();

        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(alunoPayload("Aluno Um", cpf, emailUnico("aluno")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(cpf));
    }

    @Test
    void alunoComCpfJaCadastradoEhRejeitado() throws Exception {
        Usuario admin = criarUsuario(emailUnico("dup-admin"), "SenhaForte123", "ADMIN");
        String cpf = cpfValidoAleatorio();
        criarAluno(admin, "Aluno Original", cpf, emailUnico("orig"));

        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(alunoPayload("Outro Aluno", cpf, emailUnico("outro")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("cpf"))
                .andExpect(jsonPath("$.erro").isNotEmpty());
    }

    @Test
    void cpfComFormatacaoDiferenteEhReconhecidoComoDuplicado() throws Exception {
        Usuario admin = criarUsuario(emailUnico("mask-admin"), "SenhaForte123", "ADMIN");
        String cpf = cpfValidoAleatorio();
        String mascarado = cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "."
                + cpf.substring(6, 9) + "-" + cpf.substring(9);
        criarAluno(admin, "Aluno Mascarado", mascarado, emailUnico("mascara"));

        // Mesmo CPF, agora sem máscara: precisa ser tratado como duplicado.
        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(alunoPayload("Aluno Sem Máscara", cpf, emailUnico("sem-mascara")))))
                .andExpect(status().isConflict());
    }

    @Test
    void cpfInvalidoEhRejeitado() throws Exception {
        Usuario admin = criarUsuario(emailUnico("inv-admin"), "SenhaForte123", "ADMIN");

        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(alunoPayload("Aluno Inválido", CPF_INVALIDO, emailUnico("invalido")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("cpf"));
    }

    @Test
    void edicaoDoMesmoAlunoMantemOProprioCpf() throws Exception {
        Usuario admin = criarUsuario(emailUnico("edit-admin"), "SenhaForte123", "ADMIN");
        Aluno aluno = criarAluno(admin, "Aluno Editado", cpfValidoAleatorio(), emailUnico("editado"));

        Map<String, Object> corpo = alunoPayload("Aluno Editado", aluno.getCpf(), aluno.getEmail());
        corpo.put("id", aluno.getId());

        mockMvc.perform(put("/api/aluno/" + aluno.getId())
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(corpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(aluno.getCpf()));
    }

    @Test
    void cpfDeFuncionarioNaoPodeSerUsadoPorAluno() throws Exception {
        Usuario admin = criarUsuario(emailUnico("cross-admin"), "SenhaForte123", "ADMIN");
        String cpfCompartilhado = cpfValidoAleatorio();

        Map<String, Object> funcionario = new HashMap<>();
        funcionario.put("nome", "Funcionário Existente");
        funcionario.put("cargo", "TECNICO");
        funcionario.put("dataAdmissao", "2024-01-10");
        funcionario.put("cpf", cpfCompartilhado);
        mockMvc.perform(post("/api/funcionario")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(funcionario)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(alunoPayload("Aluno Duplicado", cpfCompartilhado, emailUnico("cross")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("cpf"));
    }

    @Test
    void cadastrosSimultaneosComOMesmoCpfNaoGeramDuplicidade() throws Exception {
        Usuario admin = criarUsuario(emailUnico("conc-admin"), "SenhaForte123", "ADMIN");
        String token = tokenDe(admin);
        String cpf = cpfValidoAleatorio();
        String prefixo = emailUnico("conc");
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
                                .content(json(alunoPayload("Aluno " + indice, cpf,
                                        "concorrente-" + indice + "-" + prefixo))))
                        .andReturn().getResponse().getStatus();
            }));
        }
        largada.countDown();

        List<Integer> statusObtidos = new ArrayList<>();
        for (Future<Integer> futuro : futuros) {
            statusObtidos.add(futuro.get(30, java.util.concurrent.TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statusObtidos).containsOnly(200, 409);
        assertThat(statusObtidos.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(alunoRepository.existeCpf(cpf, null)).isTrue();
        assertThat(alunoRepository.findByCpf(cpf)).isPresent();
        assertThat(alunoRepository.findAll().stream()
                .filter(a -> cpf.equals(a.getCpf())).count()).isEqualTo(1);
    }

    @Test
    void emailDeAcessoDuplicadoIgnorandoMaiusculasEhRejeitado() throws Exception {
        Usuario admin = criarUsuario(emailUnico("email-admin"), "SenhaForte123", "ADMIN");
        String email = emailUnico("acesso");

        Map<String, Object> primeiro = new HashMap<>();
        primeiro.put("nome", "Pessoa Um");
        primeiro.put("email", email.toUpperCase());   // como o usuário poderia digitar
        primeiro.put("senha", "SenhaForte123");
        primeiro.put("cargo", "RECEPCIONISTA");
        mockMvc.perform(post("/api/usuario")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(primeiro)))
                .andExpect(status().isOk());

        // Mesmo e-mail, apenas com maiúsculas/minúsculas diferentes: duplicado.
        Map<String, Object> segundo = new HashMap<>(primeiro);
        segundo.put("nome", "Pessoa Dois");
        segundo.put("email", email);
        mockMvc.perform(post("/api/usuario")
                        .header("Authorization", bearer(tokenDe(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(segundo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.campo").value("emailAcesso"));

        assertThat(usuarioRepository.findByEmailIgnoreCase(email)).isPresent();
    }

    @Test
    void emailDeContatoDoAlunoPodeSeRepetir() throws Exception {
        // Decisão de negócio: o e-mail do ALUNO é contato (não é login), então dois
        // alunos da mesma família podem usar o mesmo endereço.
        Usuario admin = criarUsuario(emailUnico("fam-admin"), "SenhaForte123", "ADMIN");
        String emailFamilia = emailUnico("familia");

        criarAluno(admin, "Aluno Irmão Um", cpfValidoAleatorio(), emailFamilia);
        criarAluno(admin, "Aluno Irmão Dois", cpfValidoAleatorio(), emailFamilia);
    }

    private Aluno criarAluno(Usuario usuario, String nome, String cpf, String email) throws Exception {
        mockMvc.perform(post("/api/aluno")
                        .header("Authorization", bearer(tokenDe(usuario)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(alunoPayload(nome, cpf, email))))
                .andExpect(status().isOk());
        return alunoRepository.findByCpf(cpf.replaceAll("\\D", "")).orElseThrow();
    }
}
