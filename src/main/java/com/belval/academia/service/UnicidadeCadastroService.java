package com.belval.academia.service;

import com.belval.academia.exception.ErroDeRegraDeNegocio;
import com.belval.academia.repository.AlunoRepository;
import com.belval.academia.repository.FuncionarioRepository;
import com.belval.academia.repository.UsuarioRepository;
import com.belval.academia.util.Cpf;
import com.belval.academia.util.Normalizacao;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Regras de unicidade dos cadastros (e-mail de acesso e CPF).
 *
 * <p>Escopo definido para o modelo de dados atual:</p>
 * <ul>
 *   <li><b>E-mail</b>: só é identidade única em {@code usuario.email}, que é a
 *       conta de login. {@code aluno.email} e {@code funcionario.email} são
 *       contatos e podem se repetir (ex.: dois alunos da mesma família, ou o
 *       e-mail pessoal igual ao e-mail de acesso do funcionário) — por isso não
 *       existe restrição global entre essas tabelas.</li>
 *   <li><b>CPF</b>: identifica a pessoa física. É único dentro de cada tabela
 *       (aluno e funcionário) e, para valores <i>novos ou alterados</i>, também
 *       entre as duas tabelas — o mesmo CPF não pode virar dois cadastros
 *       novos. Registros antigos que já estejam repetidos entre as tabelas não
 *       são bloqueados na edição (o valor permanece o mesmo), para não travar
 *       dados legados.</li>
 * </ul>
 */
@Service
public class UnicidadeCadastroService {

    private final UsuarioRepository usuarioRepository;
    private final AlunoRepository alunoRepository;
    private final FuncionarioRepository funcionarioRepository;

    public UnicidadeCadastroService(UsuarioRepository usuarioRepository,
                                    AlunoRepository alunoRepository,
                                    FuncionarioRepository funcionarioRepository) {
        this.usuarioRepository = usuarioRepository;
        this.alunoRepository = alunoRepository;
        this.funcionarioRepository = funcionarioRepository;
    }

    /** Valida o e-mail da conta de acesso (identidade de login). */
    public void validarEmailAcesso(String email, Long idUsuarioAtual) {
        String normalizado = Normalizacao.email(email);
        if (normalizado == null) {
            return;
        }
        boolean existe = idUsuarioAtual == null
                ? usuarioRepository.existsByEmailIgnoreCase(normalizado)
                : usuarioRepository.existsByEmailIgnoreCaseAndIdNot(normalizado, idUsuarioAtual);
        if (existe) {
            throw new ErroDeRegraDeNegocio(HttpStatus.CONFLICT,
                    "Já existe uma conta de acesso com este e-mail.", "emailAcesso");
        }
    }

    /**
     * Valida formato e unicidade do CPF de um aluno.
     *
     * @param cpf              CPF informado (com ou sem máscara)
     * @param idAtual          id do aluno em edição ({@code null} no cadastro)
     * @param cpfArmazenado    CPF atualmente gravado no registro (para permitir manter o próprio valor)
     */
    public void validarCpfAluno(String cpf, Long idAtual, String cpfArmazenado) {
        validarCpf(cpf, idAtual, cpfArmazenado, true);
    }

    /** Idem para funcionário. */
    public void validarCpfFuncionario(String cpf, Long idAtual, String cpfArmazenado) {
        validarCpf(cpf, idAtual, cpfArmazenado, false);
    }

    private void validarCpf(String cpf, Long idAtual, String cpfArmazenado, boolean ehAluno) {
        String digitos = Cpf.somenteDigitos(cpf);
        if (digitos == null) {
            return; // CPF opcional: nada a validar.
        }
        if (!Cpf.valido(digitos)) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST,
                    "CPF inválido. Confira os dígitos informados.", "cpf");
        }

        boolean duplicadoNaTabela = ehAluno
                ? alunoRepository.existeCpf(digitos, idAtual)
                : funcionarioRepository.existeCpf(digitos, idAtual);
        if (duplicadoNaTabela) {
            throw new ErroDeRegraDeNegocio(HttpStatus.CONFLICT,
                    ehAluno
                            ? "Já existe um aluno cadastrado com este CPF."
                            : "Já existe um funcionário cadastrado com este CPF.",
                    "cpf");
        }

        // Verificação cruzada: só para valores novos/alterados.
        boolean valorAlterado = idAtual == null || !digitos.equals(Normalizacao.somenteDigitos(cpfArmazenado));
        if (!valorAlterado) {
            return;
        }
        boolean existeNoOutroCadastro = ehAluno
                ? funcionarioRepository.existeCpf(digitos, null)
                : alunoRepository.existeCpf(digitos, null);
        if (existeNoOutroCadastro) {
            throw new ErroDeRegraDeNegocio(HttpStatus.CONFLICT,
                    ehAluno
                            ? "Este CPF já está cadastrado como funcionário."
                            : "Este CPF já está cadastrado como aluno.",
                    "cpf");
        }
    }
}
