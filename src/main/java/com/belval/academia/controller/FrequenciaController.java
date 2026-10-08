package com.belval.academia.controller;

import com.belval.academia.model.Aluno;
import com.belval.academia.model.Frequencia;
import com.belval.academia.repository.AlunoRepository;
import com.belval.academia.repository.FrequenciaRepository;
import com.belval.academia.service.FrequenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/frequencia")
@CrossOrigin(origins = {"http://localhost:5173", "https://gerencia-sigma.vercel.app", "https://gerencia-gsdy7lqhm-paulowares-projects.vercel.app"})
public class FrequenciaController {

    @Autowired
    private FrequenciaRepository frequenciaRepository;

    @Autowired
    private AlunoRepository alunoRepository;

    @Autowired
    private FrequenciaService frequenciaService;

    // ===================== ENDPOINTS EXISTENTES (preservados) =====================

    @GetMapping
    public List<Frequencia> listar() {
        return frequenciaRepository.findAll();
    }

    @PostMapping
    public Frequencia registrar(@RequestBody Frequencia f) {
        return frequenciaRepository.save(f);
    }

    @GetMapping("/aluno/{alunoId}")
    public List<Frequencia> porAluno(@PathVariable Long alunoId) {
        return frequenciaRepository.findByAlunoId(alunoId);
    }

    @GetMapping("/data/{data}")
    public List<Frequencia> porData(@PathVariable String data) {
        return frequenciaRepository.findAll().stream()
                .filter(f -> f.getData() != null && f.getData().equals(LocalDate.parse(data)))
                .toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        return frequenciaRepository.findById(id)
                .map(existing -> { frequenciaRepository.deleteById(id); return ResponseEntity.ok().<Void>build(); })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===================== FLUXO QR CODE + CHECK-IN =====================

    /** Token do dia exibido no QR Code da academia (muda automaticamente a cada dia). */
    @GetMapping("/token")
    public Map<String, Object> tokenDoDia() {
        LocalDate hoje = frequenciaService.hoje();
        Map<String, Object> resp = new HashMap<>();
        resp.put("token", frequenciaService.gerarTokenQr(hoje));
        resp.put("data", hoje);
        return resp;
    }

    /**
     * Registra a presença do aluno pelo CPF (fluxo do sistema web). O backend
     * valida o token do QR, a existência do aluno, a situação cadastral, a
     * situação FINANCEIRA (mensalidade atrasada bloqueia o acesso) e a regra de
     * uma presença por dia — todas as validações vivem no
     * {@link FrequenciaService}, compartilhadas com o check-in do app
     * ({@code POST /api/entrada/validar}).
     *
     * <p>Todas as validações acontecem aqui: o cliente apenas envia o CPF e o
     * token do QR, nunca decide se o aluno pode entrar.</p>
     */
    @PostMapping("/check-in")
    public ResponseEntity<Map<String, Object>> checkIn(@RequestBody CheckInRequest req) {
        FrequenciaService.ResultadoCheckIn qrInvalido =
                frequenciaService.validarTokenQr(req == null ? null : req.getToken());
        if (qrInvalido != null) {
            return ResponseEntity.status(qrInvalido.status()).body(qrInvalido.corpo());
        }
        if (req.getCpf() == null || req.getCpf().replaceAll("\\D", "").isEmpty()) {
            return erro(HttpStatus.BAD_REQUEST, "Informe um CPF válido.");
        }
        Optional<Aluno> alunoOpt = alunoRepository.localizarPorCpf(req.getCpf());
        if (alunoOpt.isEmpty()) {
            return erro(HttpStatus.NOT_FOUND, "Aluno não encontrado para o CPF informado.");
        }
        FrequenciaService.ResultadoCheckIn resultado = frequenciaService.concluirEntrada(alunoOpt.get());
        return ResponseEntity.status(resultado.status()).body(resultado.corpo());
    }

    /** Resumo de frequência do aluno, usado no detalhe do aluno em /alunos. */
    @GetMapping("/resumo/{alunoId}")
    public Map<String, Object> resumo(@PathVariable Long alunoId) {
        return frequenciaService.resumo(alunoId);
    }

    // ===================== HELPERS =====================

    private ResponseEntity<Map<String, Object>> erro(HttpStatus status, String mensagem) {
        Map<String, Object> body = new HashMap<>();
        body.put("erro", mensagem);
        return ResponseEntity.status(status).body(body);
    }

    public static class CheckInRequest {
        private String token;
        private String cpf;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getCpf() {
            return cpf;
        }

        public void setCpf(String cpf) {
            this.cpf = cpf;
        }
    }
}
