package com.belval.academia.service;

import com.belval.academia.model.Usuario;
import com.belval.academia.repository.UsuarioRepository;
import com.belval.academia.util.Normalizacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Autenticação por usuário/senha e cuidado com as senhas.
 *
 * <p>As senhas novas são sempre gravadas com BCrypt. Usuários antigos, cuja
 * senha ainda está em texto puro, continuam entrando: a senha é conferida em
 * tempo constante e, no primeiro login bem-sucedido, é regravada como hash
 * BCrypt — assim a base migra sozinha, sem derrubar ninguém e sem apagar dados.</p>
 */
@Service
public class AutenticacaoService {

    private static final Logger log = LoggerFactory.getLogger(AutenticacaoService.class);

    /**
     * Hash fictício (BCrypt) usado quando o e-mail não existe: a comparação é
     * executada mesmo assim para que o tempo de resposta não revele se o e-mail
     * está cadastrado.
     */
    private static final String HASH_FICTICIO =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AutenticacaoService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Confere as credenciais. Devolve o usuário quando o login é válido.
     * Nunca registra a senha em log.
     */
    public Optional<Usuario> autenticar(String email, String senha) {
        String emailNormalizado = Normalizacao.email(email);

        if (emailNormalizado == null || senha == null || senha.isEmpty()) {
            passwordEncoder.matches(senha == null ? "" : senha, HASH_FICTICIO);
            return Optional.empty();
        }

        Optional<Usuario> encontrado = usuarioRepository.findByEmailIgnoreCase(emailNormalizado);
        if (encontrado.isEmpty()) {
            passwordEncoder.matches(senha, HASH_FICTICIO);
            return Optional.empty();
        }

        Usuario usuario = encontrado.get();
        String senhaArmazenada = usuario.getSenha();
        if (senhaArmazenada == null || senhaArmazenada.isBlank()) {
            return Optional.empty();
        }

        if (ehHashBcrypt(senhaArmazenada)) {
            return passwordEncoder.matches(senha, senhaArmazenada) ? Optional.of(usuario) : Optional.empty();
        }

        // ===== Senha legada (texto puro) =====
        // Comparação em tempo constante e migração automática para BCrypt.
        if (!senhasIguais(senha, senhaArmazenada)) {
            return Optional.empty();
        }
        usuario.setSenha(passwordEncoder.encode(senha));
        usuarioRepository.save(usuario);
        log.info("Senha do usuário id={} migrada de texto puro para BCrypt.", usuario.getId());
        return Optional.of(usuario);
    }

    /** Codifica uma senha nova (cadastro/alteração) com BCrypt. */
    public String codificar(String senha) {
        return passwordEncoder.encode(senha);
    }

    /** Indica se o valor armazenado já é um hash BCrypt (e não texto puro). */
    public static boolean ehHashBcrypt(String senha) {
        return senha != null
                && (senha.startsWith("$2a$") || senha.startsWith("$2b$") || senha.startsWith("$2y$"));
    }

    /** Compara duas senhas em tempo constante (evita vazar informação por tempo). */
    public static boolean senhasIguais(String informada, String armazenada) {
        return MessageDigest.isEqual(
                informada.getBytes(StandardCharsets.UTF_8),
                armazenada.getBytes(StandardCharsets.UTF_8));
    }
}
