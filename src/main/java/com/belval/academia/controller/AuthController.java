package com.belval.academia.controller;

import com.belval.academia.dto.AlunoSessaoDTO;
import com.belval.academia.dto.LoginRequestDTO;
import com.belval.academia.dto.LoginUnificadoResponseDTO;
import com.belval.academia.dto.UsuarioResponseDTO;
import com.belval.academia.exception.ErroDeRegraDeNegocio;
import com.belval.academia.model.Aluno;
import com.belval.academia.model.Usuario;
import com.belval.academia.security.TokenService;
import com.belval.academia.security.UsuarioAutenticado;
import com.belval.academia.service.AlunoAcessoService;
import com.belval.academia.service.AutenticacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Autenticação unificada: uma única entrada para toda a academia.
 *
 * <p>A tela de login do aplicativo passou a pedir apenas e-mail e senha. Este
 * controller resolve a identidade sem que o usuário precise informar se é aluno,
 * administrador ou técnico:</p>
 * <ol>
 *   <li>a conta é procurada entre os <b>usuários</b> (equipe: ADMIN,
 *       RECEPCIONISTA, FINANCEIRO, TECNICO) — a mesma base já usada pelo
 *       frontend web em {@code POST /api/usuario/login}, preservado intacto;</li>
 *   <li>não havendo conta de equipe com este e-mail, procura-se entre os
 *       <b>alunos</b> com senha de acesso definida.</li>
 * </ol>
 *
 * <p>Regras de segurança mantidas:</p>
 * <ul>
 *   <li>confirmação de senha idêntica à das contas de acesso (BCrypt, com
 *       tempo de resposta constante para não revelar se o e-mail existe);</li>
 *   <li>mensagem de erro ÚNICA e genérica para e-mail inexistente, senha errada
 *       ou aluno sem senha cadastrada;</li>
 *   <li>o cargo devolvido vem do banco, nunca do corpo da requisição;</li>
 *   <li>o token é o mesmo JWT já assinado pelo projeto, com o subject do aluno
 *       no formato {@code aluno:<id>} — nenhum segundo mecanismo de token.</li>
 * </ul>
 *
 * <p>Contas sem área correspondente no aplicativo (RECEPCIONISTA e FINANCEIRO)
 * recebem token válido, porém o app não possui telas para elas; a autorização de
 * cada rota continua sendo decidida pelo {@code SecurityConfig}.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Mensagem única para qualquer falha de credencial. */
    private static final String ERRO_CREDENCIAIS = "E-mail ou senha inválidos.";

    private final AutenticacaoService autenticacaoService;
    private final AlunoAcessoService alunoAcessoService;
    private final TokenService tokenService;

    public AuthController(AutenticacaoService autenticacaoService,
                          AlunoAcessoService alunoAcessoService,
                          TokenService tokenService) {
        this.autenticacaoService = autenticacaoService;
        this.alunoAcessoService = alunoAcessoService;
        this.tokenService = tokenService;
    }

    /**
     * Login unificado por e-mail e senha. Sucesso devolve o token e o perfil
     * resolvido; qualquer falha devolve 401 com a mesma mensagem.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequestDTO login) {
        String email = login == null ? null : login.getEmail();
        String senha = login == null ? null : login.getSenha();

        // 1) Conta de equipe (ADMIN / RECEPCIONISTA / FINANCEIRO / TECNICO).
        Optional<Usuario> usuario = autenticacaoService.autenticar(email, senha);
        if (usuario.isPresent()) {
            Usuario conta = usuario.get();
            LoginUnificadoResponseDTO resposta = new LoginUnificadoResponseDTO();
            resposta.setToken(tokenService.gerarToken(conta));
            resposta.setCargo(conta.getCargo());
            resposta.setUsuario(new UsuarioResponseDTO(conta));
            return ResponseEntity.ok(resposta);
        }

        // 2) Aluno com senha de acesso definida.
        Optional<Aluno> aluno = alunoAcessoService.autenticar(email, senha);
        if (aluno.isPresent()) {
            Aluno conta = aluno.get();
            LoginUnificadoResponseDTO resposta = new LoginUnificadoResponseDTO();
            resposta.setToken(tokenService.gerarTokenAluno(conta));
            resposta.setCargo(TokenService.CARGO_ALUNO);
            resposta.setAluno(new AlunoSessaoDTO(conta));
            return ResponseEntity.ok(resposta);
        }

        // Genérico de propósito: não revela se o e-mail está cadastrado.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("erro", ERRO_CREDENCIAIS));
    }

    /**
     * Confere se o token guardado no aparelho ainda é válido e devolve o perfil
     * correspondente.
     *
     * <p>Usado ao reabrir o aplicativo: a simples presença de um token local não
     * é prova de sessão viva. Aqui a assinatura, a expiração e a identidade são
     * reconferidas no backend a cada abertura, e um token de aluno removido do
     * banco deixa de ser aceito imediatamente.</p>
     */
    @GetMapping("/sessao")
    public ResponseEntity<?> sessao() {
        Long alunoId = UsuarioAutenticado.alunoId();
        if (alunoId != null) {
            Aluno aluno = alunoAcessoService.buscar(alunoId);
            LoginUnificadoResponseDTO resposta = new LoginUnificadoResponseDTO();
            resposta.setCargo(TokenService.CARGO_ALUNO);
            resposta.setAluno(new AlunoSessaoDTO(aluno));
            return ResponseEntity.ok(resposta);
        }

        Long usuarioId = UsuarioAutenticado.id();
        Usuario conta = usuarioId == null ? null
                : autenticacaoService.buscarPorId(usuarioId).orElse(null);
        if (conta == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.UNAUTHORIZED,
                    "Sessão inválida. Faça login novamente.");
        }

        LoginUnificadoResponseDTO resposta = new LoginUnificadoResponseDTO();
        resposta.setCargo(conta.getCargo());
        resposta.setUsuario(new UsuarioResponseDTO(conta));
        return ResponseEntity.ok(resposta);
    }
}