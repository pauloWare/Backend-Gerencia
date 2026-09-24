package com.belval.academia.controller;

import com.belval.academia.model.Aluno;
import com.belval.academia.model.Frequencia;
import com.belval.academia.repository.AlunoRepository;
import com.belval.academia.repository.FrequenciaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/frequencia")
@CrossOrigin(origins = {"http://localhost:5173", "https://gerencia-sigma.vercel.app", "https://gerencia-gsdy7lqhm-paulowares-projects.vercel.app"})
public class FrequenciaController {

    // Segredo que deriva o token diário do QR Code da academia (SHA-256).
    // O QR identifica o DIA da academia, não um aluno específico.
    private static final String SEGREDO_QR = "academia-tcc-presenca-2026";

    @Autowired
    private FrequenciaRepository frequenciaRepository;

    @Autowired
    private AlunoRepository alunoRepository;

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
        LocalDate hoje = dataHoraServidor().toLocalDateTime().toLocalDate();
        Map<String, Object> resp = new HashMap<>();
        resp.put("token", gerarToken(hoje));
        resp.put("data", hoje);
        return resp;
    }

    /**
     * Registra a presença do aluno pelo CPF. O backend valida o token do QR,
     * a existência do aluno, a situação e a regra de uma presença por dia.
     */
    @PostMapping("/check-in")
    public ResponseEntity<Map<String, Object>> checkIn(@RequestBody CheckInRequest req) {
        java.sql.Timestamp agora = dataHoraServidor();
        LocalDate hoje = agora.toLocalDateTime().toLocalDate();

        if (req == null || req.getToken() == null || !gerarToken(hoje).equals(req.getToken().trim())) {
            return erro(HttpStatus.BAD_REQUEST, "QR Code inválido ou expirado. Solicite um novo QR à recepção.");
        }
        if (req.getCpf() == null || req.getCpf().replaceAll("\\D", "").isEmpty()) {
            return erro(HttpStatus.BAD_REQUEST, "Informe um CPF válido.");
        }
        Optional<Aluno> alunoOpt = alunoRepository.localizarPorCpf(req.getCpf());
        if (alunoOpt.isEmpty()) {
            return erro(HttpStatus.NOT_FOUND, "Aluno não encontrado para o CPF informado.");
        }
        Aluno aluno = alunoOpt.get();
        if (!"ATIVO".equals(aluno.getSituacao())) {
            return erro(HttpStatus.BAD_REQUEST,
                    "Aluno não está apto a registrar presença (situação: " + aluno.getSituacao() + ").");
        }
        if (frequenciaRepository.existsByAlunoIdAndData(aluno.getId(), hoje)) {
            return erro(HttpStatus.CONFLICT, "Este aluno já registrou presença hoje.");
        }

        Frequencia frequencia = new Frequencia();
        frequencia.setAluno(aluno);
        frequencia.setData(hoje);
        frequencia.setHoraEntrada(agora.toLocalDateTime().toLocalTime().withNano(0));
        Frequencia salva = frequenciaRepository.save(frequencia);

        Map<String, Object> alunoDto = new HashMap<>();
        alunoDto.put("id", aluno.getId());
        alunoDto.put("nome", aluno.getNome());

        Map<String, Object> resp = new HashMap<>();
        resp.put("mensagem", "Presença registrada com sucesso!");
        resp.put("aluno", alunoDto);
        resp.put("data", salva.getData());
        resp.put("horaEntrada", salva.getHoraEntrada());
        return ResponseEntity.ok(resp);
    }

    /** Resumo de frequência do aluno, usado no detalhe do aluno em /alunos. */
    @GetMapping("/resumo/{alunoId}")
    public Map<String, Object> resumo(@PathVariable Long alunoId) {
        LocalDate hoje = dataHoraServidor().toLocalDateTime().toLocalDate();
        YearMonth mesAtual = YearMonth.from(hoje);
        LocalDate inicioMes = mesAtual.atDay(1);

        List<Frequencia> registros = frequenciaRepository.findByAlunoId(alunoId).stream()
                .filter(f -> f.getData() != null)
                .sorted(Comparator.comparing(Frequencia::getData).reversed())
                .toList();

        // Presenças distintas no mês atual (protege contra registros duplicados antigos)
        long presencasMes = registros.stream()
                .filter(f -> !f.getData().isBefore(inicioMes) && !f.getData().isAfter(hoje))
                .map(Frequencia::getData)
                .distinct()
                .count();
        long diasConsiderados = contarDiasUteis(inicioMes, hoje);
        Integer percentual = diasConsiderados > 0
                ? (int) Math.round((presencasMes * 100.0) / diasConsiderados)
                : null;

        Map<String, Object> resp = new HashMap<>();
        resp.put("presencasMes", presencasMes);
        resp.put("diasConsiderados", diasConsiderados);
        resp.put("percentual", percentual);
        resp.put("totalPresencas", registros.size());
        resp.put("ultimaPresenca", registros.isEmpty() ? null : resumirRegistro(registros.get(0)));
        resp.put("historico", registros.stream().limit(10).map(this::resumirRegistro).toList());
        return resp;
    }

    // ===================== HELPERS =====================

    /** Data/hora oficial do registro vindas do banco (SQL Server), nunca do navegador. */
    private java.sql.Timestamp dataHoraServidor() {
        java.sql.Timestamp agora = frequenciaRepository.dataHoraServidor();
        return agora != null ? agora : java.sql.Timestamp.valueOf(java.time.LocalDateTime.now());
    }

    /** Token do dia = SHA-256(segredo + data). Invalida automaticamente quando o dia muda. */
    private String gerarToken(LocalDate dia) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((SEGREDO_QR + "|" + dia).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível", e);
        }
    }

    private Map<String, Object> resumirRegistro(Frequencia f) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", f.getId());
        m.put("data", f.getData());
        m.put("horaEntrada", f.getHoraEntrada());
        return m;
    }

    private ResponseEntity<Map<String, Object>> erro(HttpStatus status, String mensagem) {
        Map<String, Object> body = new HashMap<>();
        body.put("erro", mensagem);
        return ResponseEntity.status(status).body(body);
    }

    /** Dias úteis (segunda a sexta), inclusive nas duas pontas. */
    private long contarDiasUteis(LocalDate inicio, LocalDate fim) {
        long total = 0;
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            switch (d.getDayOfWeek()) {
                case SATURDAY, SUNDAY -> { }
                default -> total++;
            }
        }
        return total;
    }

    public static class CheckInRequest {
        private String token;
        private String cpf;
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getCpf() { return cpf; }
        public void setCpf(String cpf) { this.cpf = cpf; }
    }
}
