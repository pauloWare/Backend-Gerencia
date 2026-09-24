package com.belval.academia.controller;

import com.belval.academia.model.Funcionario;
import com.belval.academia.repository.FuncionarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/funcionario")
@CrossOrigin(origins = "http://localhost:5173")
public class FuncionarioController {

    @Autowired
    private FuncionarioRepository funcionarioRepository;

    @GetMapping
    public List<Funcionario> listar() {
        return funcionarioRepository.findAll();
    }

    @PostMapping
    public Funcionario criar(@RequestBody Funcionario f) {
        return funcionarioRepository.save(f);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Funcionario> buscar(@PathVariable Long id) {
        return funcionarioRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Funcionario> atualizar(@PathVariable Long id, @RequestBody Funcionario f) {
        return funcionarioRepository.findById(id)
                .map(existing -> {
                    // O cadastro atual não envia o vínculo com a conta de acesso
                    // (usuarioId); preserva o vínculo existente para não apagar
                    // o relacionamento funcionario -> usuario em uma atualização.
                    if (f.getUsuarioId() == null) {
                        f.setUsuarioId(existing.getUsuarioId());
                    }
                    f.setId(id);
                    return ResponseEntity.ok(funcionarioRepository.save(f));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        return funcionarioRepository.findById(id)
                .map(existing -> { funcionarioRepository.deleteById(id); return ResponseEntity.ok().<Void>build(); })
                .orElse(ResponseEntity.notFound().build());
    }
}
