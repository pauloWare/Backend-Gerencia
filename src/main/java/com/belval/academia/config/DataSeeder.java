package com.belval.academia.config;

import com.belval.academia.model.Usuario;
import com.belval.academia.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Contas de desenvolvimento do TCC.
 * Cria as 4 contas de teste (uma por cargo) caso ainda não existam.
 * Se a conta já existir, apenas garante nome/senha/cargo corretos (sem duplicar).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;

    private static final List<ContaTeste> CONTAS = List.of(
            new ContaTeste("Administrador", "admin@academia.com", "admin123", "ADMIN"),
            new ContaTeste("Recepção", "recepcao@academia.com", "recepcao123", "RECEPCIONISTA"),
            new ContaTeste("Financeiro", "financeiro@academia.com", "financeiro123", "FINANCEIRO"),
            new ContaTeste("Técnico", "tecnico@academia.com", "tecnico123", "TECNICO")
    );

    @Override
    public void run(String... args) {
        // Base de dev/teste ja populada (massa de dados coerente): o seed NAO
        // recria as contas antigas a cada inicializacao. Ele atua apenas em
        // banco vazio (bootstrap), evitando poluir a base com usuarios legados.
        if (usuarioRepository.count() > 0) {
            return;
        }
        for (ContaTeste conta : CONTAS) {
            Optional<Usuario> existente = usuarioRepository.findByEmail(conta.email);
            if (existente.isPresent()) {
                Usuario u = existente.get();
                boolean alterado = false;
                if (!conta.nome.equals(u.getNome())) {
                    u.setNome(conta.nome);
                    alterado = true;
                }
                if (!conta.senha.equals(u.getSenha())) {
                    u.setSenha(conta.senha);
                    alterado = true;
                }
                if (!conta.cargo.equals(u.getCargo())) {
                    u.setCargo(conta.cargo);
                    alterado = true;
                }
                if (alterado) {
                    usuarioRepository.save(u);
                }
            } else {
                Usuario u = new Usuario();
                u.setNome(conta.nome);
                u.setEmail(conta.email);
                u.setSenha(conta.senha);
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