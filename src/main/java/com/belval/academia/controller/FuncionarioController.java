package com.belval.academia.controller;

import com.belval.academia.dto.FuncionarioRequestDTO;
import com.belval.academia.dto.FuncionarioResponseDTO;
import com.belval.academia.service.FuncionarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Cadastro de funcionários e da conta de acesso vinculada.
 *
 * <p>As respostas usam {@link FuncionarioResponseDTO}: os dados cadastrais, o
 * e-mail de acesso e o vínculo com a conta — nunca a senha nem o hash.</p>
 */
@RestController
@RequestMapping("/api/funcionario")
@CrossOrigin(origins = "http://localhost:5173")
public class FuncionarioController {

    private final FuncionarioService funcionarioService;

    public FuncionarioController(FuncionarioService funcionarioService) {
        this.funcionarioService = funcionarioService;
    }

    @GetMapping
    public List<FuncionarioResponseDTO> listar() {
        return funcionarioService.listar();
    }

    @PostMapping
    public FuncionarioResponseDTO criar(@RequestBody FuncionarioRequestDTO dados) {
        return funcionarioService.criar(dados);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FuncionarioResponseDTO> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(funcionarioService.buscar(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FuncionarioResponseDTO> atualizar(@PathVariable Long id,
                                                            @RequestBody FuncionarioRequestDTO dados) {
        return ResponseEntity.ok(funcionarioService.atualizar(id, dados));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        funcionarioService.deletar(id);
        return ResponseEntity.ok().<Void>build();
    }
}
