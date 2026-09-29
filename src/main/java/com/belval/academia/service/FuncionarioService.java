package com.belval.academia.service;

import com.belval.academia.dto.FuncionarioRequestDTO;
import com.belval.academia.dto.FuncionarioResponseDTO;
import com.belval.academia.exception.ErroDeRegraDeNegocio;
import com.belval.academia.model.Funcionario;
import com.belval.academia.model.Usuario;
import com.belval.academia.repository.FuncionarioRepository;
import com.belval.academia.repository.UsuarioRepository;
import com.belval.academia.security.UsuarioAutenticado;
import com.belval.academia.util.Normalizacao;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Cadastro de funcionários e da respectiva conta de acesso.
 *
 * <p>Regras de credencial aplicadas aqui:</p>
 * <ul>
 *   <li>a senha só é gravada como hash BCrypt (nunca em texto puro);</li>
 *   <li>atualização cadastral sem o campo {@code senha} <b>preserva</b> a senha atual;</li>
 *   <li>o e-mail de acesso é único (comparação sem diferenciar maiúsculas/minúsculas);</li>
 *   <li>somente ADMIN cria/gerencia conta com perfil ADMIN (evita privilégio
 *       administrativo indevido por quem só gerencia funcionários);</li>
 *   <li>senha/hash não são devolvidos em nenhuma resposta (ver DTO).</li>
 * </ul>
 */
@Service
public class FuncionarioService {

    private static final List<String> CARGOS_VALIDOS =
            List.of("ADMIN", "RECEPCIONISTA", "FINANCEIRO", "TECNICO");
    private static final List<String> SITUACOES_VALIDAS =
            List.of("ATIVO", "INATIVO", "AFASTADO");
    private static final int SENHA_MINIMA = 6;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final FuncionarioRepository funcionarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UnicidadeCadastroService unicidade;

    public FuncionarioService(FuncionarioRepository funcionarioRepository,
                              UsuarioRepository usuarioRepository,
                              PasswordEncoder passwordEncoder,
                              UnicidadeCadastroService unicidade) {
        this.funcionarioRepository = funcionarioRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.unicidade = unicidade;
    }

    @Transactional(readOnly = true)
    public List<FuncionarioResponseDTO> listar() {
        // Uma única consulta de contas evita N+1 ao montar o DTO.
        Map<Long, Usuario> contas = usuarioRepository.findAll().stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
        return funcionarioRepository.findAll().stream()
                .map(f -> new FuncionarioResponseDTO(f, contaDe(f, contas)))
                .toList();
    }

    @Transactional(readOnly = true)
    public FuncionarioResponseDTO buscar(Long id) {
        Funcionario f = funcionarioRepository.findById(id)
                .orElseThrow(() -> new ErroDeRegraDeNegocio(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));
        return new FuncionarioResponseDTO(f, contaDe(f));
    }

    @Transactional
    public FuncionarioResponseDTO criar(FuncionarioRequestDTO dto) {
        Funcionario f = new Funcionario();
        aplicarDadosCadastrais(f, dto, null);
        Usuario conta = sincronizarContaDeAcesso(null, dto, f);
        if (conta != null) {
            f.setUsuarioId(conta.getId());
        }
        Funcionario salvo = funcionarioRepository.save(f);
        return new FuncionarioResponseDTO(salvo, conta);
    }

    @Transactional
    public FuncionarioResponseDTO atualizar(Long id, FuncionarioRequestDTO dto) {
        Funcionario existente = funcionarioRepository.findById(id)
                .orElseThrow(() -> new ErroDeRegraDeNegocio(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));

        // O vínculo com a conta é preservado: o cliente não define isso.
        Usuario contaAtual = contaDe(existente);

        aplicarDadosCadastrais(existente, dto, id);

        Usuario conta = sincronizarContaDeAcesso(contaAtual, dto, existente);
        if (conta != null) {
            existente.setUsuarioId(conta.getId());
        }
        Funcionario salvo = funcionarioRepository.save(existente);
        return new FuncionarioResponseDTO(salvo, conta);
    }

    @Transactional
    public void deletar(Long id) {
        Funcionario existente = funcionarioRepository.findById(id)
                .orElseThrow(() -> new ErroDeRegraDeNegocio(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));
        Long usuarioId = existente.getUsuarioId();
        funcionarioRepository.delete(existente);
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(usuarioRepository::delete);
        }
    }

    // ========================================================================
    // DADOS CADASTRAIS
    // ========================================================================

    private void aplicarDadosCadastrais(Funcionario f, FuncionarioRequestDTO dto, Long idAtual) {
        String nome = Normalizacao.texto(dto.getNome());
        if (nome == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Informe o nome do funcionário.", "nome");
        }
        String cargo = Normalizacao.texto(dto.getCargo());
        if (cargo == null || !CARGOS_VALIDOS.contains(cargo.toUpperCase())) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Cargo inválido.", "cargo");
        }
        String situacao = Normalizacao.texto(dto.getSituacao());
        if (situacao != null && !SITUACOES_VALIDAS.contains(situacao.toUpperCase())) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Situação inválida.", "situacao");
        }
        if (dto.getDataAdmissao() == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Informe a data de admissão.", "dataAdmissao");
        }
        String emailContato = Normalizacao.email(dto.getEmail());
        if (emailContato != null && !EMAIL.matcher(emailContato).matches()) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Informe um e-mail válido.", "email");
        }

        // Valida o CPF ANTES de sobrescrever o valor atual: na edição, o próprio
        // funcionário pode manter o CPF que já possui.
        unicidade.validarCpfFuncionario(dto.getCpf(), idAtual, f.getCpf());

        f.setNome(nome);
        f.setCargo(cargo.toUpperCase());
        if (situacao != null) {
            f.setSituacao(situacao.toUpperCase());
        } else if (f.getSituacao() == null) {
            f.setSituacao("ATIVO");
        }
        f.setDataAdmissao(dto.getDataAdmissao());
        f.setSalario(dto.getSalario());
        f.setCpf(Normalizacao.somenteDigitos(dto.getCpf()));
        f.setDataNascimento(dto.getDataNascimento());
        f.setTelefone(Normalizacao.texto(dto.getTelefone()));
        f.setEmail(emailContato);
        f.setCep(Normalizacao.somenteDigitos(dto.getCep()));
        f.setLogradouro(Normalizacao.texto(dto.getLogradouro()));
        f.setNumero(Normalizacao.texto(dto.getNumero()));
        f.setComplemento(Normalizacao.texto(dto.getComplemento()));
        f.setBairro(Normalizacao.texto(dto.getBairro()));
        f.setCidade(Normalizacao.texto(dto.getCidade()));
        f.setEstado(Normalizacao.texto(dto.getEstado()) == null
                ? null
                : dto.getEstado().trim().toUpperCase());
    }

    // ========================================================================
    // CONTA DE ACESSO
    // ========================================================================

    /**
     * Cria ou atualiza a conta de acesso vinculada ao funcionário.
     *
     * @param conta conta já vinculada ({@code null} quando o funcionário ainda não tem acesso)
     * @return a conta criada/atualizada, ou {@code null} quando o funcionário não tem/não pediu acesso
     */
    private Usuario sincronizarContaDeAcesso(Usuario conta, FuncionarioRequestDTO dto, Funcionario f) {
        String emailAcesso = Normalizacao.email(dto.getEmailAcesso());
        String senha = dto.getSenha();
        boolean temSenha = senha != null && !senha.isBlank();

        // Funcionário sem conta de acesso e sem pedido de criação: nada a fazer.
        if (conta == null && emailAcesso == null && !temSenha) {
            return null;
        }
        if (emailAcesso != null && !EMAIL.matcher(emailAcesso).matches()) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                    "Informe um e-mail de acesso válido.", "emailAcesso");
        }
        if (temSenha) {
            if (senha.length() < SENHA_MINIMA) {
                throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                        "A senha deve ter no mínimo " + SENHA_MINIMA + " caracteres.", "senha");
            }
            if (dto.getConfirmarSenha() != null && !senha.equals(dto.getConfirmarSenha())) {
                throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                        "As senhas não conferem.", "confirmarSenha");
            }
        }
        if (conta == null && emailAcesso == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                    "Informe o e-mail de acesso da conta de login.", "emailAcesso");
        }

        // Perfil ADMIN: só um administrador pode criar/manter esse tipo de conta.
        boolean envolveAdmin = "ADMIN".equalsIgnoreCase(f.getCargo())
                || (conta != null && "ADMIN".equalsIgnoreCase(conta.getCargo()));
        if (envolveAdmin && !UsuarioAutenticado.ehAdmin()) {
            throw new ErroDeRegraDeNegocio(HttpStatus.FORBIDDEN,
                    "Apenas administradores podem gerenciar contas de acesso com perfil ADMIN.",
                    "emailAcesso");
        }

        if (conta == null) {
            if (!temSenha) {
                throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                        "Informe a senha inicial da conta de acesso.", "senha");
            }
            unicidade.validarEmailAcesso(emailAcesso, null);
            Usuario novo = new Usuario();
            novo.setNome(f.getNome());
            novo.setEmail(emailAcesso);
            novo.setSenha(passwordEncoder.encode(senha)); // sempre com hash
            novo.setCargo(f.getCargo());
            novo.setTelefone(f.getTelefone());
            novo.setDataNascimento(f.getDataNascimento() == null ? null : f.getDataNascimento().toString());
            novo.setStatus("user");
            return usuarioRepository.save(novo);
        }

        // Edição: o que não foi enviado é preservado (inclusive a senha atual).
        if (emailAcesso != null) {
            unicidade.validarEmailAcesso(emailAcesso, conta.getId());
            conta.setEmail(emailAcesso);
        }
        if (temSenha) {
            conta.setSenha(passwordEncoder.encode(senha)); // sempre com hash
        }
        conta.setNome(f.getNome());
        conta.setCargo(f.getCargo());
        conta.setTelefone(f.getTelefone());
        if (f.getDataNascimento() != null) {
            conta.setDataNascimento(f.getDataNascimento().toString());
        }
        return usuarioRepository.save(conta);
    }


    private Usuario contaDe(Funcionario f) {
        return f.getUsuarioId() == null
                ? null
                : usuarioRepository.findById(f.getUsuarioId()).orElse(null);
    }

    private Usuario contaDe(Funcionario f, Map<Long, Usuario> contasPorId) {
        return f.getUsuarioId() == null ? null : contasPorId.get(f.getUsuarioId());
    }
}
