package com.belval.academia.security;

import com.belval.academia.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * Geração e validação dos tokens de acesso (JWT assinado com HS256).
 *
 * <p>Regras aplicadas:</p>
 * <ul>
 *   <li>a chave vem de variável de ambiente ({@code JWT_SECRET}); nenhum segredo
 *       de produção é versionado no código;</li>
 *   <li>o token tem prazo de validade ({@code JWT_EXPIRATION_MINUTES});</li>
 *   <li>o token carrega apenas o id do usuário (subject) e o cargo — sem senha,
 *       hash, CPF ou outros dados pessoais;</li>
 *   <li>a assinatura e a expiração são verificadas em toda requisição
 *       (ver {@link JwtAuthenticationFilter}).</li>
 * </ul>
 */
@Component
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    /** Emissor gravado/validado no token (evita aceitar token de outro sistema). */
    static final String EMISSOR = "gerencia-academia";

    /** HS256 exige, no mínimo, 256 bits (32 caracteres/bytes). */
    private static final int TAMANHO_MINIMO_SEGREDO = 32;

    private final SecretKey chave;
    private final Duration validade;

    public TokenService(
            @Value("${app.security.jwt.secret:}") String segredo,
            @Value("${app.security.jwt.expiration-minutes:120}") long minutosDeValidade) {
        this.chave = criarChave(segredo);
        this.validade = Duration.ofMinutes(minutosDeValidade > 0 ? minutosDeValidade : 120);
    }

    /** Gera o token do usuário com o prazo de validade configurado. */
    public String gerarToken(Usuario usuario) {
        return gerarToken(usuario, validade);
    }

    /** Gera o token com um prazo específico (usado em testes de expiração). */
    public String gerarToken(Usuario usuario, Duration prazoDeValidade) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .issuer(EMISSOR)
                .subject(String.valueOf(usuario.getId()))
                .claim("cargo", usuario.getCargo())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(prazoDeValidade)))
                .signWith(chave, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida assinatura, emissor e expiração. Devolve as informações do token
     * somente quando ele é legítimo; em qualquer outro caso, {@code Optional.empty()}.
     */
    public Optional<Claims> validarToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chave)
                    .requireIssuer(EMISSOR)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            // Token inválido/expirado/assinatura errada: não vaza detalhes ao cliente.
            log.debug("Token rejeitado: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Identificador do usuário (subject) presente em um token já validado. */
    public Optional<Long> idDoUsuario(Claims claims) {
        try {
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (NumberFormatException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private SecretKey criarChave(String segredo) {
        if (segredo != null && !segredo.isBlank()) {
            if (segredo.trim().length() < TAMANHO_MINIMO_SEGREDO) {
                throw new IllegalStateException(
                        "JWT_SECRET inválido: informe uma chave com pelo menos "
                                + TAMANHO_MINIMO_SEGREDO + " caracteres.");
            }
            return Keys.hmacShaKeyFor(segredo.trim().getBytes(StandardCharsets.UTF_8));
        }
        // Sem segredo configurado: gera uma chave aleatória para o ambiente local.
        // Os tokens deixam de valer quando o servidor reiniciar.
        byte[] aleatoria = new byte[TAMANHO_MINIMO_SEGREDO];
        new SecureRandom().nextBytes(aleatoria);
        log.warn("JWT_SECRET não configurado: gerando chave aleatória para esta execução. "
                + "Em produção defina JWT_SECRET (>= {} caracteres) para manter as sessões "
                + "válidas entre reinícios.", TAMANHO_MINIMO_SEGREDO);
        return Keys.hmacShaKeyFor(Base64.getEncoder().encode(aleatoria));
    }
}
