package com.belval.academia.security;

import com.belval.academia.model.Usuario;
import com.belval.academia.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Lê o token do cabeçalho {@code Authorization: Bearer <token>} e monta a
 * autenticação da requisição.
 *
 * <p>O token NUNCA é aceito por parâmetro de URL ({@code ?token=}), nem é
 * considerado válido apenas por existir: a assinatura e a expiração são
 * verificadas a cada requisição e o usuário é reconferido no banco (assim um
 * usuário removido ou com cargo alterado perde/ganha acesso imediatamente).</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final TokenService tokenService;
    private final UsuarioRepository usuarioRepository;
    private final AuthenticationEntryPoint entryPoint;

    @Autowired
    public JwtAuthenticationFilter(TokenService tokenService,
                                   UsuarioRepository usuarioRepository,
                                   RestAuthenticationEntryPoint entryPoint) {
        this.tokenService = tokenService;
        this.usuarioRepository = usuarioRepository;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(PREFIXO)) {
            // Sem token: a decisão (401 ou rota pública) fica com o SecurityConfig.
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIXO.length()).trim();
        Optional<Claims> claims = tokenService.validarToken(token);
        if (claims.isEmpty()) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response,
                    new BadCredentialsException("Token ausente, inválido ou expirado."));
            return;
        }

        Optional<Long> id = tokenService.idDoUsuario(claims.get());
        Optional<Usuario> usuario = id.flatMap(usuarioRepository::findById);
        if (usuario.isEmpty()) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response,
                    new BadCredentialsException("Token ausente, inválido ou expirado."));
            return;
        }

        String cargo = usuario.get().getCargo();
        List<SimpleGrantedAuthority> autoridades = (cargo == null || cargo.isBlank())
                ? List.of()
                : List.of(new SimpleGrantedAuthority("ROLE_" + cargo.trim().toUpperCase()));

        UsernamePasswordAuthenticationToken autenticacao = new UsernamePasswordAuthenticationToken(
                String.valueOf(usuario.get().getId()), null, autoridades);
        SecurityContextHolder.getContext().setAuthentication(autenticacao);

        filterChain.doFilter(request, response);
    }
}
