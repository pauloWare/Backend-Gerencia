package com.belval.academia.repository;

import com.belval.academia.model.Aluno;
import com.belval.academia.util.Normalizacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Objects;
import java.util.Optional;

public interface AlunoRepository extends JpaRepository<Aluno, Long> {

    Optional<Aluno> findByCpf(String cpf);

    boolean existsByCpf(String cpf);

    /**
     * Localiza o aluno pelo e-mail de LOGIN (sem diferenciar maiúsculas e
     * minúsculas). Só devolve quem já tem senha de acesso definida — aluno sem
     * senha cadastrada não é uma conta de acesso e nunca autentica.
     *
     * <p>A busca ignora e-mails vazios: {@code senha IS NOT NULL} não é
     * suficiente porque registros antigos podem ter a coluna como string vazia.
     * Por isso filtramos também em memória (volume baixo, como no resto do
     * projeto — ver {@link #localizarPorCpf(String)}).</p>
     */
    default Optional<Aluno> localizarPorEmailDeAcesso(String email) {
        String normalizado = Normalizacao.email(email);
        if (normalizado == null) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(a -> a.getEmail() != null && a.getEmail().equalsIgnoreCase(normalizado))
                .filter(Aluno::possuiSenhaDeAcesso)
                .findFirst();
    }

    /**
     * Verifica se outro aluno (diferente de {@code idIgnorado}) já usa este
     * e-mail como identidade de login. Só quem tem senha de acesso conta:
     * assim dois alunos da mesma família podem continuar com o mesmo e-mail de
     * contato enquanto um deles ainda não tem conta liberada.
     */
    default boolean existeEmailDeAcesso(String email, Long idIgnorado) {
        String normalizado = Normalizacao.email(email);
        if (normalizado == null) {
            return false;
        }
        return findAll().stream()
                .filter(a -> a.getEmail() != null && a.getEmail().equalsIgnoreCase(normalizado))
                .filter(Aluno::possuiSenhaDeAcesso)
                .filter(a -> idIgnorado == null || !Objects.equals(a.getId(), idIgnorado))
                .findFirst()
                .isPresent();
    }

    /**
     * Existência de CPF considerando apenas os dígitos, para reconhecer como
     * duplicados valores gravados com ou sem máscara (registros legados).
     * O {@code idIgnorado} permite que, na edição, o aluno mantenha o próprio CPF.
     */
    default boolean existeCpf(String cpf, Long idIgnorado) {
        String digitos = Normalizacao.somenteDigitos(cpf);
        if (digitos == null) {
            return false;
        }
        return findAll().stream()
                .filter(a -> a.getCpf() != null)
                .filter(a -> idIgnorado == null || !Objects.equals(a.getId(), idIgnorado))
                .anyMatch(a -> digitos.equals(Normalizacao.somenteDigitos(a.getCpf())));
    }

    /**
     * Existência de telefone considerando apenas os dígitos (reconhece como
     * duplicados valores gravados com máscara, com ou sem código do país 55).
     * O {@code idIgnorado} permite que, na edição, o aluno mantenha o próprio
     * telefone sem gerar falso conflito consigo mesmo.
     */
    default boolean existeTelefone(String telefone, Long idIgnorado) {
        String normalizado = Normalizacao.telefone(telefone);
        if (normalizado == null) {
            return false;
        }
        return findAll().stream()
                .filter(a -> a.getTelefone() != null)
                .filter(a -> idIgnorado == null || !Objects.equals(a.getId(), idIgnorado))
                .anyMatch(a -> normalizado.equals(Normalizacao.telefone(a.getTelefone())));
    }

    /**
     * Localiza um aluno pelo CPF aceitando o valor com ou sem máscara.
     * Primeiro tenta a busca exata (findByCpf); se não encontrar, compara apenas os
     * dígitos, pois o CPF pode estar gravado formatado (ex.: "123.456.789-01").
     */
    default Optional<Aluno> localizarPorCpf(String cpf) {
        if (cpf == null) return Optional.empty();
        String digitos = cpf.replaceAll("\\D", "");
        if (digitos.isEmpty()) return Optional.empty();
        Optional<Aluno> exato = findByCpf(cpf);
        if (exato.isPresent()) return exato;
        return findAll().stream()
                .filter(a -> a.getCpf() != null && a.getCpf().replaceAll("\\D", "").equals(digitos))
                .findFirst();
    }
}
