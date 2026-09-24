package com.belval.academia.controller;

import com.belval.academia.model.Aluno;
import com.belval.academia.repository.AlunoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/aluno")
@CrossOrigin(origins = {"http://localhost:5173", "https://gerencia-sigma.vercel.app", "https://gerencia-gsdy7lqhm-paulowares-projects.vercel.app"})
public class AlunoController {

    @Autowired
    private AlunoRepository alunoRepository;

    @GetMapping
    public List<Aluno> listar() {
        return alunoRepository.findAll();
    }

    @PostMapping
    public Aluno criar(@RequestBody Aluno aluno) {
        return alunoRepository.save(aluno);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Aluno> buscar(@PathVariable Long id) {
        return alunoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Aluno> atualizar(@PathVariable Long id, @RequestBody Aluno aluno) {
        return alunoRepository.findById(id)
                .map(existing -> {
                    // O cadastro atual não envia o atestado médico; preserva o
                    // arquivo existente para não apagá-lo em uma atualização.
                    if (aluno.getAtestadoMedicoBase64() == null) {
                        aluno.setAtestadoMedicoBase64(existing.getAtestadoMedicoBase64());
                    }
                    aluno.setId(id);
                    return ResponseEntity.ok(alunoRepository.save(aluno));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Localiza um aluno pelo CPF (usado pelo fluxo público de registro de presença).
     * Aceita CPF com ou sem máscara e devolve apenas o mínimo necessário para
     * identificar o aluno no check-in, sem expor dados pessoais adicionais.
     */
    @GetMapping("/cpf/{cpf}")
    public ResponseEntity<Map<String, Object>> buscarPorCpf(@PathVariable String cpf) {
        Optional<Aluno> aluno = alunoRepository.localizarPorCpf(cpf);
        if (aluno.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Aluno a = aluno.get();
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", a.getId());
        dto.put("nome", a.getNome());
        dto.put("situacao", a.getSituacao());
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deletar(@PathVariable Long id) {
        return alunoRepository.findById(id)
                .map(aluno -> {
                    Map<String, String> response = new HashMap<>();
                    
                    if ("INATIVO".equals(aluno.getSituacao())) {
                        response.put("mensagem", "Aluno já está inativo.");
                        return ResponseEntity.ok(response);
                    }
                    
                    aluno.setSituacao("INATIVO");
                    alunoRepository.save(aluno);
                    
                    response.put("mensagem", "Aluno inativado com sucesso.");
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
