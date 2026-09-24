package com.belval.academia.controller;

import com.belval.academia.dto.LoginRequestDTO;
import com.belval.academia.dto.UsuarioResponseDTO;
import com.belval.academia.model.Usuario;
import com.belval.academia.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @PostMapping("/usuario/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO login) {
        List<Usuario> usuarios = usuarioRepository.findByEmailAndSenha(login.getEmail(), login.getSenha());
        if (usuarios.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(Map.of("erro", "Email ou senha inválidos."));
        }

        Usuario usuario = usuarios.get(0);
        Map<String, Object> response = new HashMap<>();
        response.put("token", UUID.randomUUID().toString());
        response.put("usuario", new UsuarioResponseDTO(usuario));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/usuario")
    public ResponseEntity<List<UsuarioResponseDTO>> getUsuario(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String senha
    ) {
        List<Usuario> usuarios;
        if (email != null && senha != null) {
            usuarios = usuarioRepository.findByEmailAndSenha(email, senha);
        } else {
            usuarios = usuarioRepository.findAll();
        }
        return ResponseEntity.ok(toDTO(usuarios));
    }

    @PostMapping("/usuario")
    public ResponseEntity<Usuario> criarUsuario(@RequestBody Usuario usuario) {
        Usuario novoUsuario = usuarioRepository.save(usuario);
        return ResponseEntity.ok(novoUsuario);
    }

    private List<UsuarioResponseDTO> toDTO(List<Usuario> usuarios) {
        return usuarios.stream().map(UsuarioResponseDTO::new).toList();
    }
}