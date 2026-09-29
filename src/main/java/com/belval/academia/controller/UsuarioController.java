package com.belval.academia.controller;

import com.belval.academia.dto.CadastroUsuarioDTO;
import com.belval.academia.dto.LoginRequestDTO;
import com.belval.academia.dto.UsuarioResponseDTO;
import com.belval.academia.exception.ErroDeRegraDeNegocio;
import com.belval.academia.model.Usuario;
import com.belval.academia.repository.UsuarioRepository;
import com.belval.academia.security.TokenService;
import com.belval.academia.service.AutenticacaoService;
import com.belval.academia.service.UnicidadeCadastroService;
import com.belval.academia.util.Normalizacao;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Contas de acesso (login) do sistema.
 *
 * <p>Importante: nenhuma rota devolve a senha ou o hash da senha. A listagem
 * usa {@link UsuarioResponseDTO}. O login responde o mesmo JSON de antes
 * ({@code token} + {@code usuario}) para não quebrar web/mobile.</p>
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class UsuarioController {

    private static final List<String> CARGOS_VALIDOS =
            List.of("ADMIN", "RECEPCIONISTA", "FINANCEIRO", "TECNICO");
    private static final int SENHA_MINIMA = 6;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UsuarioRepository usuarioRepository;
    private final AutenticacaoService autenticacaoService;
    private final UnicidadeCadastroService unicidade;
    private final TokenService tokenService;

    public UsuarioController(UsuarioRepository usuarioRepository,
                             AutenticacaoService autenticacaoService,
                             UnicidadeCadastroService unicidade,
                             TokenService tokenService) {
        this.usuarioRepository = usuarioRepository;
        this.autenticacaoService = autenticacaoService;
        this.unicidade = unicidade;
        this.tokenService = tokenService;
    }

    /**
     * Login: valida as credenciais (BCrypt, com migração transparente de senhas
     * antigas em texto puro) e devolve o token assinado + os dados do usuário.
     */
    @PostMapping("/usuario/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO login) {
        String email = login == null ? null : login.getEmail();
        String senha = login == null ? null : login.getSenha();

        Optional<Usuario> usuario = autenticacaoService.autenticar(email, senha);
        if (usuario.isEmpty()) {
            // Mensagem genérica: não revela se o e-mail existe.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("erro", "Email ou senha inválidos."));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("token", tokenService.gerarToken(usuario.get()));
        response.put("usuario", new UsuarioResponseDTO(usuario.get()));
        return ResponseEntity.ok(response);
    }

    /**
     * Lista as contas de acesso (sem senha). A autenticação por e-mail/senha em
     * parâmetro de URL foi removida: credenciais nunca devem trafegar na query
     * string (ficam registradas em logs/proxies).
     */
    @GetMapping("/usuario")
    public ResponseEntity<List<UsuarioResponseDTO>> getUsuario() {
        return ResponseEntity.ok(toDTO(usuarioRepository.findAll()));
    }

    /**
     * Cria uma conta de acesso. Restrito a ADMIN pelo SecurityConfig; a senha é
     * gravada com BCrypt e o retorno nunca inclui a senha.
     */
    @PostMapping("/usuario")
    public ResponseEntity<UsuarioResponseDTO> criarUsuario(@RequestBody CadastroUsuarioDTO dados) {
        if (dados == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Dados obrigatórios ausentes.", "nome");
        }
        String nome = Normalizacao.texto(dados.getNome());
        if (nome == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Informe o nome.", "nome");
        }
        String email = Normalizacao.email(dados.getEmail());
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Informe um e-mail válido.", "email");
        }
        String senha = dados.getSenha();
        if (senha == null || senha.length() < SENHA_MINIMA) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                    "A senha deve ter no mínimo " + SENHA_MINIMA + " caracteres.", "senha");
        }
        String cargo = Normalizacao.texto(dados.getCargo());
        if (cargo == null || !CARGOS_VALIDOS.contains(cargo.toUpperCase())) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Cargo inválido.", "cargo");
        }
        unicidade.validarEmailAcesso(email, null);

        Usuario novo = new Usuario();
        novo.setNome(nome);
        novo.setEmail(email);
        novo.setSenha(autenticacaoService.codificar(senha));
        novo.setCargo(cargo.toUpperCase());
        novo.setTelefone(Normalizacao.texto(dados.getTelefone()));
        novo.setSexo(Normalizacao.texto(dados.getSexo()));
        novo.setDataNascimento(Normalizacao.texto(dados.getDataNascimento()));
        novo.setStatus("user");

        return ResponseEntity.ok(new UsuarioResponseDTO(usuarioRepository.save(novo)));
    }

    private List<UsuarioResponseDTO> toDTO(List<Usuario> usuarios) {
        return usuarios.stream().map(UsuarioResponseDTO::new).toList();
    }
}
