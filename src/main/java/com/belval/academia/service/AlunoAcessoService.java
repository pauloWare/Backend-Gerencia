package com.belval.academia.service;

import com.belval.academia.dto.AtualizarPerfilAlunoDTO;
import com.belval.academia.exception.ErroDeRegraDeNegocio;
import com.belval.academia.model.Aluno;
import com.belval.academia.repository.AlunoRepository;
import com.belval.academia.repository.UsuarioRepository;
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
 *   <li><b>O que é editável.</b> Somente contato (e-mail/telefone), endereço
 *       estruturado e contato de emergência. Nome, CPF, data de nascimento e
 *       sexo são protegidos (correção via recepção) e não existem no DTO de
 *       edição. Plano, situação, mensalidades, observações, senha e demais
 *       dados internos também não existem aqui.</li>
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

    public AlunoAcessoService(AlunoRepository alunoRepository,
                              UsuarioRepository usuarioRepository,
                              PasswordEncoder passwordEncoder,
                              @Value("${app.upload.foto-max-bytes:1000000}") int fotoMaxBytes) {
        this.alunoRepository = alunoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
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
     * Edição direta do próprio aluno: contato, endereço e emergência.
     *
     * <p>Nome/CPF/nascimento/sexo NÃO chegam aqui (fora do DTO).</p>
     *
     * @param alunoId id do token (nunca do cliente)
     * @param dados somente campos editáveis
     */
    @Transactional
    public Aluno atualizarPerfil(Long alunoId, AtualizarPerfilAlunoDTO dados) {
        Aluno aluno = buscar(alunoId);
        if (dados == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                    "Dados obrigatórios ausentes.", "email");
        }
        aplicarContato(aluno, alunoId, dados);
        aplicarEndereco(aluno, dados);
        aplicarEmergencia(aluno, dados);
        return alunoRepository.save(aluno);
    }

    private void aplicarContato(Aluno aluno, Long alunoId, AtualizarPerfilAlunoDTO dados) {
        if (dados.getEmail() != null) {
            String email = Normalizacao.email(dados.getEmail());
            if (email == null) {
                throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Informe o e-mail.", "email");
            }
            if (email.length() > 150) {
                throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                        "O e-mail deve ter no máximo 150 caracteres.", "email");
            }
            if (!EMAIL.matcher(email).matches()) {
                throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Informe um e-mail válido.", "email");
            }
            String atual = aluno.getEmail() == null ? "" : aluno.getEmail();
            if (aluno.possuiSenhaDeAcesso() && !email.equalsIgnoreCase(atual)) {
                if (alunoRepository.existeEmailDeAcesso(email, alunoId)) {
                    throw new ErroDeRegraDeNegocio(HttpStatus.CONFLICT,
                            "Este e-mail já está em uso por outro aluno.", "email");
                }
                if (emailPertenceAEquipe(email)) {
                    throw new ErroDeRegraDeNegocio(HttpStatus.CONFLICT,
                            "Este e-mail já está em uso por uma conta da equipe.", "email");
                }
            }
            aluno.setEmail(email);
        }
        if (dados.getTelefone() != null) {
            aluno.setTelefone(validarTelefone(dados.getTelefone(), "telefone",
                    "O telefone deve ter no máximo 30 caracteres.", "Informe um telefone válido."));
        }
    }

    private void aplicarEndereco(Aluno aluno, AtualizarPerfilAlunoDTO dados) {
        if (dados.getCep() != null) {
            String cepTexto = Normalizacao.texto(dados.getCep());
            if (cepTexto == null) {
                aluno.setCep(null);
            } else {
                String digitos = Normalizacao.somenteDigitos(cepTexto);
                if (digitos == null || digitos.length() != 8) {
                    throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                            "Informe um CEP com 8 dígitos.", "cep");
                }
                aluno.setCep(digitos);
            }
        }
        if (dados.getLogradouro() != null) {
            aluno.setLogradouro(validarTexto(dados.getLogradouro(), 150,
                    "A rua deve ter no máximo 150 caracteres.", "logradouro"));
        }
        if (dados.getNumero() != null) {
            aluno.setNumero(validarTexto(dados.getNumero(), 20,
                    "O número deve ter no máximo 20 caracteres.", "numero"));
        }
        if (dados.getComplemento() != null) {
            aluno.setComplemento(validarTexto(dados.getComplemento(), 60,
                    "O complemento deve ter no máximo 60 caracteres.", "complemento"));
        }
        if (dados.getBairro() != null) {
            aluno.setBairro(validarTexto(dados.getBairro(), 100,
                    "O bairro deve ter no máximo 100 caracteres.", "bairro"));
        }
    }

    private void aplicarEmergencia(Aluno aluno, AtualizarPerfilAlunoDTO dados) {
        if (dados.getContatoEmergenciaNome() != null) {
            aluno.setContatoEmergenciaNome(validarTexto(dados.getContatoEmergenciaNome(), 150,
                    "O nome do contato deve ter no máximo 150 caracteres.", "contatoEmergenciaNome"));
        }
        if (dados.getContatoEmergenciaTelefone() != null) {
            aluno.setContatoEmergenciaTelefone(validarTelefone(dados.getContatoEmergenciaTelefone(),
                    "contatoEmergenciaTelefone",
                    "O telefone do contato deve ter no máximo 30 caracteres.",
                    "Informe um telefone de contato válido."));
        }
    }

    private String validarTelefone(String valor, String campo, String msgTamanho, String msgFormato) {
        String texto = Normalizacao.texto(valor);
        if (texto == null) {
            return null;
        }
        if (texto.length() > 30) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, msgTamanho, campo);
        }
        if (!texto.matches("^[+()\\-\\s\\d]+$")) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, msgFormato, campo);
        }
        return texto;
    }

    private String validarTexto(String valor, int max, String msg, String campo) {
        String texto = Normalizacao.texto(valor);
        if (texto != null && texto.length() > max) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, msg, campo);
        }
        return texto;
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