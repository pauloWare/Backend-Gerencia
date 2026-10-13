package com.belval.academia.service;

import com.belval.academia.exception.ErroDeRegraDeNegocio;
import com.belval.academia.model.Aluno;
import com.belval.academia.model.AtivacaoAluno;
import com.belval.academia.repository.AlunoRepository;
import com.belval.academia.repository.AtivacaoAlunoRepository;
import com.belval.academia.util.Normalizacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Ativacao da conta mobile do aluno por codigo de uso unico.
 *
 * <p>Fluxo: a recepcao cadastra o aluno (sem senha) e recebe um codigo gerado
 * com {@link SecureRandom}; o aluno usa esse codigo UMA vez, junto do e-mail,
 * para definir a propria senha. O codigo em texto puro nunca e persistido:
 * apenas o hash BCrypt. Nunca aparece em JWT, log ou listagem.</p>
 */
@Service
public class AtivacaoAlunoService {

    private static final Logger log = LoggerFactory.getLogger(AtivacaoAlunoService.class);

    /** Hash ficticio p/ comparacao constante quando nenhum candidato existe. */
    private static final String HASH_FICTICIO =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    /** Alfabeto sem caracteres ambiguos (sem 0/O, 1/I/L). */
    private static final char[] ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    /** Tamanho do codigo exibido a recepcao. */
    public static final int CODIGO_TAMANHO = 8;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Maximo de tentativas erradas por codigo (anti-forca-bruta). */
    public static final int MAX_TENTATIVAS = 5;

    private final AtivacaoAlunoRepository ativacaoRepository;
    private final AlunoRepository alunoRepository;
    private final AlunoAcessoService alunoAcessoService;
    private final PasswordEncoder passwordEncoder;
    private final Duration validade;
    private final SecureRandom aleatorio = new SecureRandom();

    public AtivacaoAlunoService(AtivacaoAlunoRepository ativacaoRepository,
                                AlunoRepository alunoRepository,
                                AlunoAcessoService alunoAcessoService,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.ativacao.validade-minutos:4320}") long validadeMinutos) {
        this.ativacaoRepository = ativacaoRepository;
        this.alunoRepository = alunoRepository;
        this.alunoAcessoService = alunoAcessoService;
        this.passwordEncoder = passwordEncoder;
        this.validade = Duration.ofMinutes(validadeMinutos > 0 ? validadeMinutos : 4320);
    }

    /** Resultado da geracao: codigo p/ exibicao unica + flag de conta ativa. */
    public record CodigoGerado(String codigo, boolean contaJaAtiva) {
    }

    /**
     * Gera (ou substitui) o codigo de ativacao do aluno. Aluno que ja tem
     * senha nao recebe codigo novo (conta ja ativa).
     */
    @Transactional
    public CodigoGerado gerarPara(Aluno aluno) {
        if (aluno == null || aluno.getId() == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.NOT_FOUND, "Aluno não encontrado.");
        }
        Aluno alvo = alunoRepository.findById(aluno.getId())
                .orElseThrow(() -> new ErroDeRegraDeNegocio(HttpStatus.NOT_FOUND, "Aluno não encontrado."));
        if (alvo.possuiSenhaDeAcesso()) {
            return new CodigoGerado(null, true);
        }
        String email = Normalizacao.email(alvo.getEmail());
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                    "O aluno precisa ter um e-mail válido cadastrado para gerar o código.", "email");
        }
        if (alunoAcessoService.emailPertenceAEquipe(email)) {
            throw new ErroDeRegraDeNegocio(HttpStatus.CONFLICT,
                    "Este e-mail já está em uso por uma conta da equipe.", "email");
        }
        String codigo = gerarCodigo();
        Optional<AtivacaoAluno> existente = ativacaoRepository.findByAlunoId(alvo.getId());
        AtivacaoAluno ativacao = existente.orElseGet(AtivacaoAluno::new);
        ativacao.setAluno(alvo);
        ativacao.setCodigoHash(passwordEncoder.encode(codigo));
        ativacao.setExpiraEm(Instant.now().plus(validade));
        ativacao.setTentativas(0);
        ativacao.setConsumidoEm(null);
        ativacaoRepository.save(ativacao);
        log.info("Código de ativação gerado para o aluno id={}.", alvo.getId());
        return new CodigoGerado(codigo, false);
    }

    private String gerarCodigo() {
        StringBuilder sb = new StringBuilder(CODIGO_TAMANHO);
        for (int i = 0; i < CODIGO_TAMANHO; i++) {
            sb.append(ALFABETO[aleatorio.nextInt(ALFABETO.length)]);
        }
        return sb.toString();
    }

    /** Mensagem unica do fluxo publico (nao revela se o e-mail existe). */
    static final String ERRO_ATIVACAO = "Não foi possível ativar a conta. Confira o e-mail e o código.";

    /**
     * Ativa a conta: valida e-mail + codigo, define a senha (hash BCrypt) e
     * consome o codigo atomicamente. Sem sessao ao final: o aluno entra depois
     * pelo login normal.
     */
    @Transactional
    public void ativar(String email, String codigo, String senha, String confirmarSenha) {
        String emailNormalizado = Normalizacao.email(email);
        String codigoLimpo = Normalizacao.texto(codigo);
        if (emailNormalizado == null || codigoLimpo == null) {
            passwordEncoder.matches(codigo == null ? "" : codigo, HASH_FICTICIO);
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, ERRO_ATIVACAO, "codigo");
        }
        List<AtivacaoAluno> candidatos = ativacaoRepository.candidatosPorEmail(emailNormalizado);
        AtivacaoAluno ativacao = null;
        for (AtivacaoAluno candidato : candidatos) {
            if (passwordEncoder.matches(codigoLimpo, candidato.getCodigoHash())) {
                ativacao = candidato;
                break;
            }
        }
        if (ativacao == null) {
            Instant agoraFalha = Instant.now();
            for (AtivacaoAluno candidato : candidatos) {
                if (candidato.isPendente(agoraFalha) && candidato.getTentativas() < MAX_TENTATIVAS) {
                    candidato.setTentativas(candidato.getTentativas() + 1);
                    ativacaoRepository.save(candidato);
                }
            }
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, ERRO_ATIVACAO, "codigo");
        }
        AtivacaoAluno travada = ativacaoRepository.buscarComTrava(ativacao.getAluno().getId())
                .orElseThrow(() -> new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, ERRO_ATIVACAO, "codigo"));
        Instant agora = Instant.now();
        if (travada.getConsumidoEm() != null || travada.getTentativas() >= MAX_TENTATIVAS
                || !travada.isPendente(agora)) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, ERRO_ATIVACAO, "codigo");
        }
        validarSenha(senha, confirmarSenha);
        Aluno aluno = alunoRepository.findById(travada.getAluno().getId())
                .orElseThrow(() -> new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, ERRO_ATIVACAO, "codigo"));
        if (aluno.getEmail() == null || !aluno.getEmail().equalsIgnoreCase(emailNormalizado)) {
            travada.setTentativas(travada.getTentativas() + 1);
            ativacaoRepository.save(travada);
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, ERRO_ATIVACAO, "codigo");
        }
        if (aluno.possuiSenhaDeAcesso()) {
            travada.setConsumidoEm(agora);
            ativacaoRepository.save(travada);
            throw new ErroDeRegraDeNegocio(HttpStatus.CONFLICT,
                    "Esta conta já está ativada. Entre com seu e-mail e senha.", "email");
        }
        if (alunoRepository.existeEmailDeAcesso(emailNormalizado, aluno.getId())
                || alunoAcessoService.emailPertenceAEquipe(emailNormalizado)) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, ERRO_ATIVACAO, "codigo");
        }
        aluno.setSenha(passwordEncoder.encode(senha));
        alunoRepository.save(aluno);
        travada.setConsumidoEm(agora);
        ativacaoRepository.save(travada);
        log.info("Conta mobile do aluno id={} ativada.", aluno.getId());
    }

    private void validarSenha(String senha, String confirmarSenha) {
        if (senha == null || senha.isBlank()) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Informe a nova senha.", "senha");
        }
        if (senha.length() < AlunoAcessoService.SENHA_MINIMA) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                    "A senha deve ter no mínimo " + AlunoAcessoService.SENHA_MINIMA + " caracteres.", "senha");
        }
        if (confirmarSenha != null && !senha.equals(confirmarSenha)) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "As senhas não conferem.", "confirmarSenha");
        }
    }
}

