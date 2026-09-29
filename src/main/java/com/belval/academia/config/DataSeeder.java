package com.belval.academia.config;

import com.belval.academia.model.Usuario;
import com.belval.academia.repository.UsuarioRepository;
import com.belval.academia.util.Normalizacao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Contas de desenvolvimento do TCC.
 *
 * <p>Cria as 4 contas de teste (uma por cargo) apenas quando a base está vazia
 * (bootstrap). A senha das contas criadas é gravada com BCrypt — nunca em texto
 * puro. Se a conta já existe, os dados não são sobrescritos e a senha atual é
 * sempre preservada.</p>
 */
@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final List<ContaTeste> CONTAS = List.of(
            new ContaTeste("Administrador", "admin@academia.com", "admin123", "ADMIN"),
            new ContaTeste("Recepção", "recepcao@academia.com", "recepcao123", "RECEPCIONISTA"),
            new ContaTeste("Financeiro", "financeiro@academia.com", "financeiro123", "FINANCEIRO"),
            new ContaTeste("Técnico", "tecnico@academia.com", "tecnico123", "TECNICO")
    );

    @Override
    public void run(String... args) {
        // Base de dev/teste já populada (massa de dados coerente): o seed NÃO
        // recria as contas antigas a cada inicialização. Ele atua apenas em
        // banco vazio (bootstrap), evitando poluir a base com usuários legados.
        if (usuarioRepository.count() > 0) {
            return;
        }
        for (ContaTeste conta : CONTAS) {
            Optional<Usuario> existente = usuarioRepository.findByEmailIgnoreCase(conta.email);
            if (existente.isPresent()) {
                Usuario u = existente.get();
                boolean alterado = false;
                if (!conta.nome.equals(u.getNome())) {
                    u.setNome(conta.nome);
                    alterado = true;
                }
                if (!conta.cargo.equals(u.getCargo())) {
                    u.setCargo(conta.cargo);
                    alterado = true;
                }
                // A senha existente é preservada de propósito (nunca regravada).
                if (alterado) {
                    usuarioRepository.save(u);
                }
            } else {
                Usuario u = new Usuario();
                u.setNome(conta.nome);
                u.setEmail(Normalizacao.email(conta.email));
                // Senha sempre com hash: nem a base de desenvolvimento guarda texto puro.
                u.setSenha(passwordEncoder.encode(conta.senha));
                u.setCargo(conta.cargo);
                u.setStatus("user");
                usuarioRepository.save(u);
            }
        }
    }

    private static class ContaTeste {
        final String nome;
        final String email;
        final String senha;
        final String cargo;

        ContaTeste(String nome, String email, String senha, String cargo) {
            this.nome = nome;
            this.email = email;
            this.senha = senha;
            this.cargo = cargo;
        }
    }
}
