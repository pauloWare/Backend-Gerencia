package com.belval.academia.security;

import com.belval.academia.model.Aluno;
import com.belval.academia.model.Usuario;
import com.belval.academia.repository.AlunoRepository;
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
    private final AlunoRepository alunoRepository;
    private final AuthenticationEntryPoint entryPoint;

    @Autowired
    public JwtAuthenticationFilter(TokenService tokenService,
                                   UsuarioRepository usuarioRepository,
                                   AlunoRepository alunoRepository,
                                   RestAuthenticationEntryPoint entryPoint) {
        this.tokenService = tokenService;
        this.usuarioRepository = usuarioRepository;
        this.alunoRepository = alunoRepository;
        this.entryPoint = entryPoint;
    }

    /** Cargo gravado no token (nunca lido do cliente), ou {@code null}. */
    private static String cargoDoToken(Claims claims) {
        Object cargo = claims == null ? null : claims.get("cargo");
        return cargo == null ? null : String.valueOf(cargo);
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

        // ===== Token de ALUNO (portal mobile) =====
        // O subject vem como "aluno:<id>" e o cargo do token precisa ser ALUNO.
        // A identidade é sempre reconferida no banco (aluno removido perde acesso).
        if (tokenService.ehTokenDeAluno(claims.get())) {
            if (!TokenService.CARGO_ALUNO.equalsIgnoreCase(cargoDoToken(claims.get()))) {
                SecurityContextHolder.clearContext();
                entryPoint.commence(request, response,
                        new BadCredentialsException("Token ausente, inválido ou expirado."));
                return;
            }
            Optional<Long> idAluno = tokenService.idDoAluno(claims.get());
            Optional<Aluno> aluno = idAluno.flatMap(alunoRepository::findById);
            if (aluno.isEmpty()) {
                SecurityContextHolder.clearContext();
                entryPoint.commence(request, response,
                        new BadCredentialsException("Token ausente, inválido ou expirado."));
                return;
            }
            // O aluno NUNCA recebe autoridade administrativa: apenas ROLE_ALUNO.
            UsernamePasswordAuthenticationToken autenticacaoAluno = new UsernamePasswordAuthenticationToken(
                    TokenService.PREFIXO_ALUNO + aluno.get().getId(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + TokenService.CARGO_ALUNO)));
            SecurityContextHolder.getContext().setAuthentication(autenticacaoAluno);
            filterChain.doFilter(request, response);
            return;
        }

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
