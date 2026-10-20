package com.belval.academia.controller;

import com.belval.academia.dto.DefinirSenhaAlunoDTO;
import com.belval.academia.exception.ErroDeRegraDeNegocio;
import com.belval.academia.model.Aluno;
import com.belval.academia.repository.AlunoRepository;
import com.belval.academia.service.AlunoAcessoService;
import com.belval.academia.service.AtivacaoAlunoService;
import com.belval.academia.service.UnicidadeCadastroService;
import com.belval.academia.util.Normalizacao;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/aluno")
@CrossOrigin(origins = {"http://localhost:5173", "https://gerencia-sigma.vercel.app", "https://gerencia-gsdy7lqhm-paulowares-projects.vercel.app"})
public class AlunoController {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AlunoRepository alunoRepository;
    private final UnicidadeCadastroService unicidade;
    private final AlunoAcessoService alunoAcessoService;
    private final AtivacaoAlunoService ativacaoService;
    private final PasswordEncoder passwordEncoder;

    public AlunoController(AlunoRepository alunoRepository,
                           UnicidadeCadastroService unicidade,
                           AlunoAcessoService alunoAcessoService,
                           AtivacaoAlunoService ativacaoService,
                           PasswordEncoder passwordEncoder) {
        this.alunoRepository = alunoRepository;
        this.unicidade = unicidade;
        this.alunoAcessoService = alunoAcessoService;
        this.ativacaoService = ativacaoService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<Aluno> listar() {
        return alunoRepository.findAll();
    }

    /**
     * Cadastra o aluno e gera o código de ativação do acesso mobile.
     *
     * <p>Compatibilidade: o corpo continua sendo o ALUNO (mesmo contrato de
     * antes). O código (uso único, temporário) vai no cabeçalho
     * {@code X-Codigo-Ativacao} — exibido UMA vez à recepção, nunca em
     * listagens, logs ou consultas. Conta já ativa: cabeçalho
     * {@code X-Conta-Ativa: true}, sem código novo.</p>
     */
    @PostMapping
    public ResponseEntity<Aluno> criar(@RequestBody Aluno aluno) {
        prepararDados(aluno, null, null, null);
        // A senha de acesso e a foto têm rotas próprias e protegidas; um
        // cadastro nunca as define por engano (a senha é definida pelo aluno
        // em POST /api/portal/ativar-conta com o código desta resposta).
        aluno.setSenha(null);
        aluno.setFotoPerfilBase64(null);
        Aluno salvo = alunoRepository.save(aluno);
        try {
            AtivacaoAlunoService.CodigoGerado gerado = ativacaoService.gerarPara(salvo);
            if (gerado.contaJaAtiva()) {
                return ResponseEntity.ok().header("X-Conta-Ativa", "true").body(salvo);
            }
            return ResponseEntity.ok()
                    .header("X-Codigo-Ativacao", gerado.codigo())
                    .header("X-Conta-Ativa", "false")
                    .body(salvo);
        } catch (ErroDeRegraDeNegocio e) {
            // Sem e-mail válido: o cadastro foi concluído; o código poderá ser
            // gerado depois (novo endpoint), sem desfazer o cadastro.
            return ResponseEntity.ok()
                    .header("X-Aviso-Ativacao", "codigo-nao-gerado")
                    .body(salvo);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Aluno> buscar(@PathVariable Long id) {
        return alunoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Aluno> atualizar(@PathVariable Long id, @RequestBody Aluno aluno) {
        return alunoRepository.findById(id)
                .map(existing -> {
                    // O cadastro atual não envia o atestado médico; preserva o
                    // arquivo existente para não apagá-lo em uma atualização.
                    if (aluno.getAtestadoMedicoBase64() == null) {
                        aluno.setAtestadoMedicoBase64(existing.getAtestadoMedicoBase64());
                    }
                    // A senha de acesso e a foto de perfil NUNCA são alteradas por
                    // esta rota: são geridas em endpoints próprios e
                    // deliberadamente separados (PUT /api/aluno/{id}/senha-acesso
                    // e o portal do aluno), preservando o que já existia.
                    aluno.setSenha(existing.getSenha());
                    aluno.setFotoPerfilBase64(existing.getFotoPerfilBase64());
                    prepararDados(aluno, id, existing.getCpf(), existing.getTelefone());
                    aluno.setId(id);
                    return ResponseEntity.ok(alunoRepository.save(aluno));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Define (ou redefine) a senha de acesso de um aluno ao aplicativo.
     *
     * <p>É a etapa de definição de credencial compatível com o projeto: a conta de
     * acesso do aluno é criada aqui, pela recepção ou pelo administrador, que já
     * são as permissões de {@code /api/aluno/**}. Nenhuma senha é inventada pelo
     * sistema — sem esta chamada, o aluno simplesmente ainda não consegue entrar
     * (e os cadastros já existentes continuam válidos e intactos).</p>
     *
     * <p>A senha é gravada com BCrypt e nunca é devolvida na resposta.</p>
     */
    @PutMapping("/{id}/senha-acesso")
    public ResponseEntity<Map<String, String>> definirSenhaAcesso(
            @PathVariable Long id,
            @RequestBody(required = false) DefinirSenhaAlunoDTO dados) {
        Aluno alvo = alunoRepository.findById(id)
                .orElseThrow(() -> new ErroDeRegraDeNegocio(HttpStatus.NOT_FOUND,
                        "Aluno não encontrado."));

        if (dados == null || dados.getSenha() == null || dados.getSenha().isBlank()) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                    "Informe a senha de acesso do aluno.", "senha");
        }
        String senha = dados.getSenha();
        if (senha.length() < AlunoAcessoService.SENHA_MINIMA) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                    "A senha deve ter no mínimo " + AlunoAcessoService.SENHA_MINIMA + " caracteres.", "senha");
        }
        if (dados.getConfirmarSenha() != null && !senha.equals(dados.getConfirmarSenha())) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "As senhas não conferem.", "confirmarSenha");
        }

        String email = Normalizacao.email(alvo.getEmail());
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                    "O aluno precisa ter um e-mail válido cadastrado para acessar o aplicativo.", "email");
        }
        // Impede que dois alunos com senha passem a compartilhar a mesma
        // identidade de login (o que tornaria o login ambíguo).
        if (alunoRepository.existeEmailDeAcesso(email, alvo.getId())) {
            throw new ErroDeRegraDeNegocio(HttpStatus.CONFLICT,
                    "Este e-mail já está em uso por outro aluno.", "email");
        }
        if (alunoAcessoService.emailPertenceAEquipe(email)) {
            throw new ErroDeRegraDeNegocio(HttpStatus.CONFLICT,
                    "Este e-mail já está em uso por uma conta da equipe.", "email");
        }

        alvo.setSenha(passwordEncoder.encode(senha)); // sempre com hash
        alunoRepository.save(alvo);

        return ResponseEntity.ok(Map.of("mensagem", "Senha de acesso do aluno atualizada."));
    }

    /**
     * Gera (ou substitui) o código de ativação do acesso mobile.
     *
     * <p>Usado quando o cadastro foi concluído sem código (ex.: sem e-mail
     * válido na hora) ou quando o código anterior expirou. O código é
     * devolvido UMA vez nesta resposta — nunca em listagens ou consultas.
     * Conta já ativa: informa sem gerar código novo.</p>
     */
    @PostMapping("/{id}/codigo-ativacao")
    public ResponseEntity<Map<String, Object>> gerarCodigoAtivacao(@PathVariable Long id) {
        Aluno alvo = alunoRepository.findById(id)
                .orElseThrow(() -> new ErroDeRegraDeNegocio(HttpStatus.NOT_FOUND,
                        "Aluno não encontrado."));
        AtivacaoAlunoService.CodigoGerado gerado = ativacaoService.gerarPara(alvo);
        Map<String, Object> resposta = new HashMap<>();
        if (gerado.contaJaAtiva()) {
            resposta.put("contaJaAtiva", true);
            resposta.put("mensagem", "Esta conta já está ativada.");
        } else {
            resposta.put("contaJaAtiva", false);
            resposta.put("codigoAtivacao", gerado.codigo());
            // O registro anterior foi substituído (índice único por aluno),
            // portanto o código antigo deixou de ser válido. A mensagem não
            // contém o código — ele só existe no corpo desta resposta.
            resposta.put("mensagem", "Novo código de ativação gerado. O código anterior foi invalidado.");
        }
        return ResponseEntity.ok(resposta);
    }

    /**
     * Localiza um aluno pelo CPF (usado pelo fluxo público de registro de presença).
     * Aceita CPF com ou sem máscara e devolve apenas o mínimo necessário para
     * identificar o aluno no check-in, sem expor dados pessoais adicionais.
     */
    @GetMapping("/cpf/{cpf}")
    public ResponseEntity<Map<String, Object>> buscarPorCpf(@PathVariable String cpf) {
        Optional<Aluno> aluno = alunoRepository.localizarPorCpf(cpf);
        if (aluno.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Aluno a = aluno.get();
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", a.getId());
        dto.put("nome", a.getNome());
        dto.put("situacao", a.getSituacao());
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deletar(@PathVariable Long id) {
        return alunoRepository.findById(id)
                .map(aluno -> {
                    Map<String, String> response = new HashMap<>();

                    if ("INATIVO".equals(aluno.getSituacao())) {
                        response.put("mensagem", "Aluno já está inativo.");
                        return ResponseEntity.ok(response);
                    }

                    aluno.setSituacao("INATIVO");
                    alunoRepository.save(aluno);

                    response.put("mensagem", "Aluno inativado com sucesso.");
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Normaliza e valida os dados cadastrais antes de gravar.
     *
     * <p>CPF: validado (dígitos verificadores) e gravado apenas com dígitos,
     * impedindo que "123.456.789-01" e "12345678901" virem dois cadastros.
     * E-mail de contato: espaços removidos e minúsculas (não é identidade de
     * login, por isso pode se repetir entre alunos — ex.: mesma família).
     * Telefone: unicidade validada (409) e gravado só com dígitos — a mesma
     * normalização da migração V5, para o índice único do banco comparar
     * valores canônicos ("(11) 98765-4321" == "11987654321").</p>
     */
    private void prepararDados(Aluno aluno, Long idAtual, String cpfArmazenado, String telefoneArmazenado) {
        if (aluno == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Dados obrigatórios ausentes.", "nome");
        }
        String nome = Normalizacao.texto(aluno.getNome());
        if (nome == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Informe o nome do aluno.", "nome");
        }
        String email = Normalizacao.email(aluno.getEmail());
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Informe um e-mail válido.", "email");
        }

        unicidade.validarCpfAluno(aluno.getCpf(), idAtual, cpfArmazenado);
        unicidade.validarTelefoneAluno(aluno.getTelefone(), idAtual, telefoneArmazenado);

        aluno.setNome(nome);
        aluno.setEmail(email);
        aluno.setCpf(Normalizacao.somenteDigitos(aluno.getCpf()));
        aluno.setTelefone(Normalizacao.telefone(aluno.getTelefone()));
        aluno.setPlano(Normalizacao.texto(aluno.getPlano()));
        aluno.setCep(Normalizacao.somenteDigitos(aluno.getCep()));
    }
}
