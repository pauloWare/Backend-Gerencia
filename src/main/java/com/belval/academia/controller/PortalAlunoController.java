package com.belval.academia.controller;

import com.belval.academia.dto.AlunoLoginRequestDTO;
import com.belval.academia.dto.AlunoPerfilDTO;
import com.belval.academia.dto.AlunoSessaoDTO;
import com.belval.academia.dto.AtualizarPerfilAlunoDTO;
import com.belval.academia.dto.FotoPerfilAlunoDTO;
import com.belval.academia.dto.MensalidadeAlunoDTO;
import com.belval.academia.exception.ErroDeRegraDeNegocio;
import com.belval.academia.model.Aluno;
import com.belval.academia.model.Mensalidade;
import com.belval.academia.repository.AlunoRepository;
import com.belval.academia.security.TokenService;
import com.belval.academia.security.UsuarioAutenticado;
import com.belval.academia.service.AlunoAcessoService;
import com.belval.academia.service.FrequenciaService;
import com.belval.academia.service.SituacaoFinanceiraService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Portal do ALUNO — endpoints usados exclusivamente pelo aplicativo mobile.
 *
 * <p>Todas as rotas (exceto o login) exigem um token de aluno
 * ({@code ROLE_ALUNO}, ver {@code SecurityConfig}). A identidade do aluno é
 * SEMPRE obtida do token assinado ({@link UsuarioAutenticado#alunoId()}) e nunca
 * de um id/CPF enviado pelo aplicativo: assim um aluno não consegue ler a
 * frequência ou as mensalidades de outro alterando parâmetros da requisição.</p>
 *
 * <p>Sobre pagamento: o backend NÃO possui integração com provedor de pagamento
 * (o fluxo {@code POST /api/mensalidade/{id}/pagar} é o registro manual/acadêmico
 * feito pelo staff na recepção). Por isso este portal apenas CONSULTA a situação
 * financeira e informa que o pagamento deve ser feito na recepção — não existe
 * e não é simulado nenhum pagamento online.</p>
 */
@RestController
@RequestMapping("/api/portal")
public class PortalAlunoController {

    private final AlunoRepository alunoRepository;
    private final TokenService tokenService;
    private final FrequenciaService frequenciaService;
    private final SituacaoFinanceiraService situacaoFinanceiraService;
    private final AlunoAcessoService alunoAcessoService;

    public PortalAlunoController(AlunoRepository alunoRepository,
                                 TokenService tokenService,
                                 FrequenciaService frequenciaService,
                                 SituacaoFinanceiraService situacaoFinanceiraService,
                                 AlunoAcessoService alunoAcessoService) {
        this.alunoRepository = alunoRepository;
        this.tokenService = tokenService;
        this.frequenciaService = frequenciaService;
        this.situacaoFinanceiraService = situacaoFinanceiraService;
        this.alunoAcessoService = alunoAcessoService;
    }

    /**
     * Login do aluno no portal mobile: e-mail + senha.
     *
     * <p>Este caminho existe para o aplicativo poder abrir uma sessão de aluno
     * mantendo exatamente a mesma regra do login unificado
     * ({@code POST /api/auth/login}): hash BCrypt, mensagem genérica e token
     * assinado com o subject {@code aluno:<id>}. CPF e data de nascimento NÃO são
     * mais usados como credencial — são dados pessoais do cadastro, guardados
     * apenas para identificação e para o registro de presença.</p>
     *
     * <p>A resposta mantém o formato antigo ({@code token} + {@code aluno}) para
     * não quebrar consumidores existentes.</p>
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) AlunoLoginRequestDTO dados) {
        Optional<Aluno> alunoOpt = dados == null
                ? Optional.empty()
                : alunoAcessoService.autenticar(dados.getEmail(), dados.getSenha());

        if (alunoOpt.isEmpty()) {
            // Genérico de propósito: não confirma se o e-mail está cadastrado.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("erro", "E-mail ou senha inválidos."));
        }

        Aluno aluno = alunoOpt.get();
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("token", tokenService.gerarTokenAluno(aluno));
        resposta.put("aluno", new AlunoSessaoDTO(aluno));
        return ResponseEntity.ok(resposta);
    }

    /** Resumo de frequência do aluno autenticado (mesma regra do sistema web). */
    @GetMapping("/frequencia")
    public Map<String, Object> frequencia() {
        return frequenciaService.resumo(alunoAutenticado());
    }

    /**
     * Situação financeira do aluno autenticado: lista das próprias mensalidades,
     * situação consolidada (EM_DIA / ATRASADA / SEM_MENSALIDADE) e o aviso de que
     * o pagamento online não está disponível (deve ser feito na recepção).
     */
    @GetMapping("/mensalidades")
    public Map<String, Object> mensalidades() {
        Long alunoId = alunoAutenticado();
        LocalDate hoje = frequenciaService.hoje();

        List<Mensalidade> lista = situacaoFinanceiraService.listar(alunoId);
        List<MensalidadeAlunoDTO> mensalidades = lista.stream()
                .map(m -> new MensalidadeAlunoDTO(m, SituacaoFinanceiraService.situacaoExibicao(m, hoje)))
                .toList();

        Map<String, Object> resposta = new HashMap<>();
        resposta.put("situacao", situacaoFinanceiraService.situacao(alunoId, hoje));
        resposta.put("hoje", hoje);
        // Sem integração com meio de pagamento: o app NÃO deve oferecer/comunicar
        // um pagamento online como se fosse real.
        resposta.put("pagamentoOnlineDisponivel", false);
        resposta.put("mensagemPagamento",
                "O pagamento online ainda não está disponível. Regularize sua mensalidade na recepção da academia.");
        resposta.put("mensalidades", mensalidades);
        return resposta;
    }

    /**
     * Perfil do aluno autenticado: nome, e-mail, telefone, CPF (mascarado),
     * data de nascimento, plano, situação e a foto de perfil em Base64.
     *
     * <p>É a fonte de verdade da tela de Perfil: os dados vêm sempre do banco,
     * nunca de um estado local do aplicativo, e o id vem do token — não da
     * requisição.</p>
     */
    @GetMapping("/perfil")
    public AlunoPerfilDTO perfil() {
        return new AlunoPerfilDTO(alunoAcessoService.buscar(alunoAutenticado()));
    }

    /**
     * Edita os dados pessoais do próprio aluno (nome, e-mail, telefone, CPF e
     * data de nascimento).
     *
     * <p>O DTO de entrada tem apenas esses cinco campos: qualquer outro dado
     * enviado é descartado, então não existe caminho para alterar cargo,
     * permissões, plano, situação ou o cadastro de outro aluno — o id do
     * registro vem sempre do token.</p>
     */
    @PutMapping("/perfil")
    public AlunoPerfilDTO atualizarPerfil(@RequestBody(required = false) AtualizarPerfilAlunoDTO dados) {
        if (dados == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Dados obrigatórios ausentes.", "nome");
        }
        Aluno atualizado = alunoAcessoService.atualizarPerfil(
                alunoAutenticado(), dados.getNome(), dados.getEmail(), dados.getTelefone(),
                dados.getCpf(), dados.getDataNascimento());
        return new AlunoPerfilDTO(atualizado);
    }

    /**
     * Grava (ou remove) a foto de perfil do aluno autenticado, em Base64.
     *
     * <p>A imagem é validada (formato PNG/JPEG/WEBP e tamanho) e guardada no
     * banco — a mesma solução já usada no projeto para a foto dos chamados, e
     * que por isso sobrevive a fechar o aplicativo e a reiniciar o servidor.
     * Um aluno só altera a própria foto, porque o id vem do token.</p>
     */
    @PutMapping("/perfil/foto")
    public AlunoPerfilDTO atualizarFoto(@RequestBody(required = false) FotoPerfilAlunoDTO dados) {
        Aluno atualizado = alunoAcessoService.atualizarFoto(
                alunoAutenticado(), dados == null ? null : dados.getFotoBase64());
        return new AlunoPerfilDTO(atualizado);
    }

    /** Id do aluno derivado do token; nunca de parâmetro enviado pelo cliente. */
    private Long alunoAutenticado() {
        Long alunoId = UsuarioAutenticado.alunoId();
        if (alunoId == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.FORBIDDEN,
                    "Acesso restrito ao aluno autenticado.");
        }
        return alunoId;
    }
}