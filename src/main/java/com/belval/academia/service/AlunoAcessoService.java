package com.belval.academia.service;

import com.belval.academia.exception.ErroDeRegraDeNegocio;
import com.belval.academia.model.Aluno;
import com.belval.academia.repository.AlunoRepository;
import com.belval.academia.repository.UsuarioRepository;
import com.belval.academia.util.Cpf;
import com.belval.academia.util.Normalizacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Acesso do ALUNO ao aplicativo: login por e-mail e senha, edição do próprio
 * perfil e da foto.
 *
 * <p>Decisões que sustentam este serviço:</p>
 * <ul>
 *   <li><b>Onde vive a senha.</b> O aluno é uma conta de acesso do app, mas não
 *       é uma conta de {@code usuario} (essa continua sendo só da equipe, gerida
 *       pelo frontend web). Guardamos o hash BCrypt em {@code aluno.senha} para
 *       não duplicar a pessoa em duas tabelas nem interferir na gestão de contas
 *       da equipe. A senha nunca é derivada de CPF ou data de nascimento.</li>
 *   <li><b>Quem pode chamar.</b> Tudo aqui usa o aluno derivado do token
 *       ({@link com.belval.academia.security.UsuarioAutenticado#alunoId()}),
 *       nunca um id enviado pelo cliente — assim um aluno não edita o perfil de
 *       outro alterando parâmetros da requisição.</li>
 *   <li><b>O que é editável.</b> Os dados pessoais do próprio aluno: nome,
 *       e-mail, telefone, CPF e data de nascimento. Plano, situação,
 *       mensalidades, observações, senha e demais dados internos continuam
 *       geridos pela academia — não existem no DTO de edição.</li>
 *   <li><b>Foto.</b> Base64 no banco (padrão já usado por {@code chamado} e
 *       {@code usuario}), com validação de formato e tamanho.</li>
 * </ul>
 */
@Service
public class AlunoAcessoService {

    private static final Logger log = LoggerFactory.getLogger(AlunoAcessoService.class);

    /**
     * Hash fictício (BCrypt) usado quando o e-mail não corresponde a nenhum aluno
     * com senha cadastrada. Garante que a comparação seja executada mesmo sem
     * usuário encontrado, para que o tempo de resposta não revele se o e-mail
     * existe (mesma técnica já usada em {@link AutenticacaoService}).
     */
    private static final String HASH_FICTICIO =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    /** Comprimento mínimo da senha, igual ao das contas de acesso da equipe. */
    public static final int SENHA_MINIMA = 6;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Prefixos de data URL aceitos na foto (o app envia sempre JPEG). */
    private static final Pattern DATA_URL_IMAGEM = Pattern.compile(
            "^data:image/(png|jpe?g|webp);base64,([A-Za-z0-9+/=]+)$");

    /**
     * Limite da foto em Base64 (~750 KB de imagem): cabe no TEXT do banco e numa
     * requisição razoável em rede móvel. Configurável por
     * {@code app.upload.foto-max-bytes}.
     */
    private final int fotoMaxBytes;

    private final AlunoRepository alunoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UnicidadeCadastroService unicidade;

    public AlunoAcessoService(AlunoRepository alunoRepository,
                              UsuarioRepository usuarioRepository,
                              PasswordEncoder passwordEncoder,
                              UnicidadeCadastroService unicidade,
                              @Value("${app.upload.foto-max-bytes:1000000}") int fotoMaxBytes) {
        this.alunoRepository = alunoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.unicidade = unicidade;
        this.fotoMaxBytes = fotoMaxBytes;
    }

    // ==================================================================
    // AUTENTICAÇÃO
    // ==================================================================

    /**
     * Confere as credenciais do aluno. Devolve o aluno quando o login é válido.
     *
     * <p>Aluno sem senha de acesso definida nunca autentica — a resposta é a
     * mesma de senha errada, para não revelar que o e-mail está cadastrado.
     * A senha nunca é registrada em log.</p>
     */
    @Transactional
    public Optional<Aluno> autenticar(String email, String senha) {
        String emailNormalizado = Normalizacao.email(email);

        if (emailNormalizado == null || senha == null || senha.isEmpty()) {
            passwordEncoder.matches(senha == null ? "" : senha, HASH_FICTICIO);
            return Optional.empty();
        }

        Optional<Aluno> encontrado = alunoRepository.localizarPorEmailDeAcesso(emailNormalizado);
        if (encontrado.isEmpty()) {
            passwordEncoder.matches(senha, HASH_FICTICIO);
            return Optional.empty();
        }

        Aluno aluno = encontrado.get();
        String senhaArmazenada = aluno.getSenha();
        if (senhaArmazenada == null || senhaArmazenada.isBlank()) {
            return Optional.empty();
        }

        // Senha legada em texto puro (o banco pode ter): comparação em tempo
        // constante e migração transparente para BCrypt, igual ao tratamento
        // das contas da equipe em AutenticacaoService.
        if (AutenticacaoService.ehHashBcrypt(senhaArmazenada)) {
            return passwordEncoder.matches(senha, senhaArmazenada) ? Optional.of(aluno) : Optional.empty();
        }

        if (!senhasIguais(senha, senhaArmazenada)) {
            return Optional.empty();
        }
        aluno.setSenha(passwordEncoder.encode(senha));
        alunoRepository.save(aluno);
        log.info("Senha de acesso do aluno id={} migrada de texto puro para BCrypt.", aluno.getId());
        return Optional.of(aluno);
    }

    /**
     * Indica se o e-mail já pertence a uma conta de equipe (ADMIN, RECEPCIONISTA,
     * FINANCEIRO, TECNICO). Usado para impedir que o aluno use, como identidade
     * de login, o e-mail de acesso de outra pessoa.
     */
    public boolean emailPertenceAEquipe(String email) {
        String normalizado = Normalizacao.email(email);
        return normalizado != null && usuarioRepository.existsByEmailIgnoreCase(normalizado);
    }

    /** Compara duas senhas em tempo constante. */
    private static boolean senhasIguais(String informada, String armazenada) {
        return MessageDigest.isEqual(
                informada.getBytes(StandardCharsets.UTF_8),
                armazenada.getBytes(StandardCharsets.UTF_8));
    }

    // ==================================================================
    // PERFIL
    // ==================================================================

    /** Recarrega o aluno a partir do banco (fonte de verdade da edição). */
    @Transactional(readOnly = true)
    public Aluno buscar(Long alunoId) {
        return alunoRepository.findById(alunoId)
                .orElseThrow(() -> new ErroDeRegraDeNegocio(HttpStatus.NOT_FOUND,
                        "Cadastro do aluno não encontrado."));
    }

    /**
     * Aplica a edição dos dados pessoais autorizada ao próprio aluno.
     *
     * <p>Recebe apenas nome, e-mail, telefone, CPF e data de nascimento — nenhum
     * id, cargo, plano ou situação chega até aqui, portanto não existe caminho
     * para o aluno alterar o próprio cargo/permissões, dados administrativos nem
     * mexer em outro cadastro.</p>
     *
     * <p>CPF e nascimento seguem a regra "nulo/vazio mantém o valor atual": o
     * aplicativo envia o que o aluno digitou e a ausência do campo nunca apaga
     * dado já gravado. CPF é normalizado para somente dígitos antes de salvar e
     * validado quanto a formato, dígitos verificadores e duplicidade (regras de
     * {@link UnicidadeCadastroService}); a data é aceita apenas em formato ISO
     * {@code AAAA-MM-DD} e nunca pode estar no futuro.</p>
     *
     * @param alunoId        id derivado do token (nunca enviado pelo cliente)
     * @param nome           novo nome (obrigatório)
     * @param email          novo e-mail de contato (também é o e-mail de login)
     * @param telefone       novo telefone (pode ser nulo)
     * @param cpf            CPF com ou sem máscara; nulo/vazio = manter o atual
     * @param dataNascimento data ISO AAAA-MM-DD; nula/vazia = manter a atual
     */
    @Transactional
    public Aluno atualizarPerfil(Long alunoId, String nome, String email, String telefone,
                                 String cpf, String dataNascimento) {
        Aluno aluno = buscar(alunoId);

        String nomeNormalizado = Normalizacao.texto(nome);
        if (nomeNormalizado == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Informe o nome.", "nome");
        }
        if (nomeNormalizado.length() > 150) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                    "O nome deve ter no máximo 150 caracteres.", "nome");
        }

        String emailNormalizado = Normalizacao.email(email);
        if (emailNormalizado == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Informe o e-mail.", "email");
        }
        if (!EMAIL.matcher(emailNormalizado).matches()) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Informe um e-mail válido.", "email");
        }

        // O e-mail do aluno é a identidade de login dele. Quando já existe senha
        // de acesso, o e-mail não pode estar em uso por outro aluno nem por uma
        // conta de equipe — isso impede que alguém ocupe o acesso de um terceiro
        // trocando o e-mail do próprio cadastro.
        if (aluno.possuiSenhaDeAcesso()) {
            if (alunoRepository.existeEmailDeAcesso(emailNormalizado, alunoId)) {
                throw new ErroDeRegraDeNegocio(HttpStatus.CONFLICT,
                        "Este e-mail já está em uso por outro aluno.", "email");
            }
            if (emailPertenceAEquipe(emailNormalizado)) {
                throw new ErroDeRegraDeNegocio(HttpStatus.CONFLICT,
                        "Este e-mail já está em uso por uma conta da equipe.", "email");
            }
        }

        String telefoneNormalizado = Normalizacao.texto(telefone);
        if (telefoneNormalizado != null && telefoneNormalizado.length() > 30) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                    "O telefone deve ter no máximo 30 caracteres.", "telefone");
        }

        // CPF: opcional e "em branco mantém o atual" — nunca apaga dado gravado.
        // Aceita com ou sem máscara; a validação (formato, dígitos verificadores,
        // duplicidade em aluno e cruzada com funcionário) é a mesma do cadastro
        // feito pela recepção, e o valor é gravado somente com dígitos — igual ao
        // padrão de AlunoController.prepararDados, mantendo compatibilidade com
        // registros legados (o repository compara por dígitos).
        if (Normalizacao.texto(cpf) != null) {
            String digitos = Cpf.somenteDigitos(cpf);
            if (digitos == null || !Cpf.valido(digitos)) {
                throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                        "CPF inválido. Confira os dígitos informados.", "cpf");
            }
            unicidade.validarCpfAluno(digitos, alunoId, aluno.getCpf());
            aluno.setCpf(digitos);
        }

        // Data de nascimento: formato ISO estrito (o app envia AAAA-MM-DD),
        // recusando data inválida ou futura. Em branco mantém a atual.
        if (Normalizacao.texto(dataNascimento) != null) {
            LocalDate nascimento;
            try {
                nascimento = LocalDate.parse(dataNascimento.trim());
            } catch (DateTimeParseException e) {
                throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                        "Informe uma data de nascimento válida (AAAA-MM-DD).", "dataNascimento");
            }
            if (nascimento.isAfter(LocalDate.now())) {
                throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                        "A data de nascimento não pode estar no futuro.", "dataNascimento");
            }
            aluno.setDataNascimento(nascimento);
        }

        aluno.setNome(nomeNormalizado);
        aluno.setEmail(emailNormalizado);
        aluno.setTelefone(telefoneNormalizado);
        return alunoRepository.save(aluno);
    }

    // ==================================================================
    // FOTO DE PERFIL
    // ==================================================================

    /**
     * Grava (ou remove, quando {@code foto} é nulo/vazio) a foto do aluno.
     *
     * <p>O dado é validado e guardado no banco como Base64 — a mesma solução já
     * usada no projeto para {@code chamado.foto_base64} e
     * {@code usuario.foto_perfil_base64}. Por estar no banco, a foto continua
     * disponível depois de fechar o aplicativo, encerrar a sessão, entrar em
     * outra sessão e após reinícios/novos deploys do servidor (um caminho de
     * arquivo temporário do aparelho não sobreviveria a nada disso).</p>
     */
    @Transactional
    public Aluno atualizarFoto(Long alunoId, String foto) {
        Aluno aluno = buscar(alunoId);
        aluno.setFotoPerfilBase64(validarFoto(foto));
        return alunoRepository.save(aluno);
    }

    /**
     * Valida a foto recebida (data URL Base64 de imagem) e devolve o valor
     * normalizado, ou {@code null} quando a foto deve ser removida.
     */
    public String validarFoto(String foto) {
        if (foto == null || foto.isBlank()) {
            return null; // remover foto
        }
        String valor = foto.trim();
        if (!DATA_URL_IMAGEM.matcher(valor).matches()) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                    "Envie uma imagem nos formatos PNG, JPEG ou WEBP.", "foto");
        }
        if (valor.length() > fotoMaxBytes) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                    "A foto é muito grande. Escolha uma imagem menor.", "foto");
        }
        return valor;
    }
}