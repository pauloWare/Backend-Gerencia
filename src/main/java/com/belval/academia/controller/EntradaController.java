package com.belval.academia.controller;

import com.belval.academia.exception.ErroDeRegraDeNegocio;
import com.belval.academia.model.Aluno;
import com.belval.academia.repository.AlunoRepository;
import com.belval.academia.security.UsuarioAutenticado;
import com.belval.academia.service.FrequenciaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Check-in do ALUNO no aplicativo mobile via QR Code da recepção.
 *
 * <p>Endpoint oficial do app ({@code POST /api/entrada/validar}). A identidade
 * do aluno vem SEMPRE do token assinado ({@link UsuarioAutenticado#alunoId()},
 * papel {@code ROLE_ALUNO}) — nunca do corpo da requisição. Um aluno não
 * consegue registrar presença por outro enviando um CPF diferente: o CPF nem é
 * lido aqui.</p>
 *
 * <p>O corpo traz apenas o token lido do QR Code da academia. Todas as
 * validações (QR do dia, situação cadastral, situação financeira fail-closed e
 * presença duplicada) ficam no {@link FrequenciaService}, compartilhadas com o
 * check-in público do sistema web ({@code POST /api/frequencia/check-in}).</p>
 */
@RestController
@RequestMapping("/api/entrada")
public class EntradaController {

    private final AlunoRepository alunoRepository;
    private final FrequenciaService frequenciaService;

    public EntradaController(AlunoRepository alunoRepository, FrequenciaService frequenciaService) {
        this.alunoRepository = alunoRepository;
        this.frequenciaService = frequenciaService;
    }

    /** Corpo do app: somente o token do QR. Qualquer outro campo é ignorado. */
    public record ValidarRequest(String token) {
    }

    /**
     * Registra a entrada do aluno autenticado.
     *
     * <p>Respostas: 200 sucesso, 400 QR inválido/situação cadastral, 404 aluno
     * do token não encontrado, 403 bloqueio financeiro (motivo
     * {@code MENSALIDADE_ATRASADA}) e 409 entrada já registrada hoje.</p>
     */
    @PostMapping("/validar")
    public ResponseEntity<Map<String, Object>> validar(@RequestBody(required = false) ValidarRequest req) {
        // 1. QR do dia (validação do backend, igual ao fluxo do web).
        FrequenciaService.ResultadoCheckIn qrInvalido =
                frequenciaService.validarTokenQr(req == null ? null : req.token());
        if (qrInvalido != null) {
            return ResponseEntity.status(qrInvalido.status()).body(qrInvalido.corpo());
        }

        // 2. Identidade do token — nunca do corpo enviado pelo cliente.
        Long alunoId = UsuarioAutenticado.alunoId();
        if (alunoId == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.FORBIDDEN,
                    "Acesso restrito ao aluno autenticado.");
        }
        Optional<Aluno> alunoOpt = alunoRepository.findById(alunoId);
        if (alunoOpt.isEmpty()) {
            Map<String, Object> body = new HashMap<>();
            body.put("erro", "Aluno não encontrado.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }

        // 3. Situação cadastral, financeira e duplicidade: mesma regra do web.
        FrequenciaService.ResultadoCheckIn resultado = frequenciaService.concluirEntrada(alunoOpt.get());
        return ResponseEntity.status(resultado.status()).body(resultado.corpo());
    }
}
