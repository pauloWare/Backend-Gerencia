package com.belval.academia;

import com.belval.academia.security.JwtAuthenticationFilter;
import com.belval.academia.security.RestAccessDeniedHandler;
import com.belval.academia.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuração de segurança da API.
 *
 * <p>Modelo adotado: autenticação stateless por token JWT no cabeçalho
 * {@code Authorization: Bearer <token>}. Os cargos (ADMIN, RECEPCIONISTA,
 * FINANCEIRO, TECNICO) definem as permissões, sempre conferidas pelo backend —
 * a interface apenas esconde o que o usuário não pode fazer.</p>
 *
 * <p>O CSRF fica desabilitado porque a autenticação não usa cookies de sessão
 * (o token é enviado explicitamente pelo cliente); sem cookie automático, não
 * existe superfície de CSRF. Se o projeto migrar para cookies, o CSRF precisa
 * ser reabilitado junto com {@code HttpOnly/Secure/SameSite}.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Codificador oficial das senhas (BCrypt com fator de custo padrão). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtAuthenticationFilter,
                                           RestAuthenticationEntryPoint authenticationEntryPoint,
                                           RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            // 1. Sem sessão em cookie -> sem superfície de CSRF.
            .csrf(AbstractHttpConfigurer::disable)

            // 2. CORS conforme origens configuradas abaixo.
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 3. Sessão stateless: a identidade vem exclusivamente do token.
            .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 4. Desabilita mecanismos que não são usados (evita endpoints/erros confusos).
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)

            // 5. 401 para não autenticado e 403 para autenticado sem permissão.
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))

            // 6. Regras de acesso. A ordem importa: as mais específicas primeiro.
            .authorizeHttpRequests(auth -> auth
                    // Preflight do navegador.
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                    // ===== Rotas públicas (usadas sem login) =====
                    .requestMatchers(HttpMethod.POST, "/api/usuario/login").permitAll()
                    // Check-in do aluno (página pública /registrar-presenca).
                    // O token do QR identifica APENAS o dia da academia (não um
                    // usuário), então continua público, como antes.
                    .requestMatchers(HttpMethod.POST, "/api/frequencia/check-in").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/aluno/cpf/**").permitAll()

                    // ===== Usuários (contas de acesso) =====
                    // A listagem é usada pela tela de Perfil de qualquer perfil logado.
                    .requestMatchers(HttpMethod.GET, "/api/usuario").authenticated()
                    // Criar/alterar/excluir contas de acesso: somente administrador.
                    .requestMatchers("/api/usuario/**").hasRole("ADMIN")

                    // ===== Financeiro =====
                    // O módulo de manutenção registra a despesa do serviço realizado.
                    .requestMatchers(HttpMethod.POST, "/api/financeiro/despesas/manutencao")
                        .hasAnyRole("ADMIN", "FINANCEIRO", "TECNICO")
                    .requestMatchers("/api/financeiro/**").hasAnyRole("ADMIN", "FINANCEIRO")

                    // ===== Funcionários (cadastro + conta de acesso) =====
                    .requestMatchers("/api/funcionario/**").hasAnyRole("ADMIN", "FINANCEIRO")

                    // ===== Manutenção / equipamentos =====
                    // Recepção e técnico podem consultar equipamentos e abrir chamados.
                    .requestMatchers(HttpMethod.GET, "/api/manutencao/equipamentos", "/api/manutencao/equipamentos/**")
                        .hasAnyRole("ADMIN", "TECNICO", "RECEPCIONISTA")
                    .requestMatchers(HttpMethod.POST, "/api/manutencao/chamados")
                        .hasAnyRole("ADMIN", "TECNICO", "RECEPCIONISTA")
                    .requestMatchers("/api/manutencao/**").hasAnyRole("ADMIN", "TECNICO")

                    // ===== Alunos =====
                    // Leitura também para o FINANCEIRO (Dashboard/Relatórios usam a lista).
                    .requestMatchers(HttpMethod.GET, "/api/aluno", "/api/aluno/**")
                        .hasAnyRole("ADMIN", "FINANCEIRO", "RECEPCIONISTA")
                    .requestMatchers("/api/aluno/**").hasAnyRole("ADMIN", "RECEPCIONISTA")

                    // ===== Mensalidades =====
                    .requestMatchers("/api/mensalidade/**").hasAnyRole("ADMIN", "FINANCEIRO", "RECEPCIONISTA")

                    // ===== Frequência (resumo usado na tela de alunos) =====
                    // O token diário do QR Code (GET /api/frequencia/token) é
                    // exibido pela recepção no painel — exige autenticação, como
                    // os demais endpoints de frequência.
                    .requestMatchers("/api/frequencia/**").hasAnyRole("ADMIN", "RECEPCIONISTA")

                    // ===== Dashboard =====
                    .requestMatchers("/api/dashboard/**").hasAnyRole("ADMIN", "FINANCEIRO")

                    // Qualquer rota nova exige, no mínimo, autenticação.
                    .anyRequest().authenticated());

        // 7. Filtro do token antes do filtro de usuário/senha do Spring Security.
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173", "https://gerencia-sigma.vercel.app", "https://gerencia-gsdy7lqhm-paulowares-projects.vercel.app"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // O token de autenticação chega no cabeçalho Authorization.
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
