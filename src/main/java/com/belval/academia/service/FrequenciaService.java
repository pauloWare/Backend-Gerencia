package com.belval.academia.service;

import com.belval.academia.model.Frequencia;
import com.belval.academia.repository.FrequenciaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Regras de presença/frequência compartilhadas entre a tela de Alunos do
 * sistema web e o portal do aluno no aplicativo.
 *
 * <p>O cálculo do percentual é exatamente o mesmo já usado pelo web: presenças
 * distintas no mês atual divididas pelos dias úteis já decorridos no mês. Não
 * existe um "denominador inventado" para o app — a regra é única.</p>
 */
@Service
public class FrequenciaService {

    /**
     * Segredo que deriva o token diário do QR Code da academia (SHA-256).
     * O QR identifica o DIA da academia, não um aluno específico.
     */
    private static final String SEGREDO_QR = "academia-tcc-presenca-2026";

    private final FrequenciaRepository frequenciaRepository;
    private final SituacaoFinanceiraService situacaoFinanceiraService;

    public FrequenciaService(FrequenciaRepository frequenciaRepository,
                             SituacaoFinanceiraService situacaoFinanceiraService) {
        this.frequenciaRepository = frequenciaRepository;
        this.situacaoFinanceiraService = situacaoFinanceiraService;
    }

    /**
     * Resultado de um check-in: status HTTP + corpo, para os controllers
     * (público do web e do app) devolverem exatamente o mesmo contrato.
     */
    public record ResultadoCheckIn(HttpStatus status, Map<String, Object> corpo) {
    }

    /**
     * Token do dia = SHA-256(segredo + data). Invalida automaticamente quando
     * o dia muda. É o valor exibido no QR Code da recepção e lido pelo app.
     */
    public String gerarTokenQr(LocalDate dia) {
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

    /**
     * Valida o token do QR contra o dia atual. Devolve {@code null} quando o
     * token é válido ou um resultado 400 quando é inválido/expirado — assim os
     * dois check-ins (web e app) respondem igual diante do mesmo QR.
     */
    public ResultadoCheckIn validarTokenQr(String tokenRecebido) {
        LocalDate hoje = hoje();
        if (tokenRecebido == null || !gerarTokenQr(hoje).equals(tokenRecebido.trim())) {
            return new ResultadoCheckIn(HttpStatus.BAD_REQUEST, Map.of("erro",
                    "QR Code inválido ou expirado. Solicite um novo QR à recepção."));
        }
        return null;
    }

    /**
     * Conclui o check-in do aluno já identificado: situação cadastral ATIVO,
     * bloqueio financeiro (fail-closed) e regra de uma presença por dia, nessa
     * ordem — todas as validações acontecem no backend, nunca no aplicativo.
     *
     * @param aluno aluno resolvido pelo controller (do CPF no web, do token no app)
     */
    public ResultadoCheckIn concluirEntrada(com.belval.academia.model.Aluno aluno) {
        Timestamp agora = agora();
        LocalDate hoje = agora.toLocalDateTime().toLocalDate();

        if (!"ATIVO".equals(aluno.getSituacao())) {
            return new ResultadoCheckIn(HttpStatus.BAD_REQUEST, Map.of("erro",
                    "Aluno não está apto a registrar presença (situação: " + aluno.getSituacao() + ")."));
        }

        // Mensalidade atrasada impede o registro. Uma falha ao consultar a
        // situação financeira também impede (fail-closed): nunca liberamos o
        // acesso apenas porque não foi possível validar.
        if (situacaoFinanceiraService.acessoBloqueadoPorMensalidade(aluno.getId(), hoje)) {
            Map<String, Object> body = new HashMap<>();
            body.put("erro",
                    "Sua mensalidade não está em dia. Por favor, procure a recepção para regularizar sua situação.");
            body.put("motivo", "MENSALIDADE_ATRASADA");
            return new ResultadoCheckIn(HttpStatus.FORBIDDEN, body);
        }

        if (frequenciaRepository.existsByAlunoIdAndData(aluno.getId(), hoje)) {
            return new ResultadoCheckIn(HttpStatus.CONFLICT,
                    Map.of("erro", "Este aluno já registrou presença hoje."));
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
        return new ResultadoCheckIn(HttpStatus.OK, resp);
    }

    /**
     * Data/hora oficial do registro vindas do banco (SQL Server/PostgreSQL),
     * nunca do relógio do navegador/celular.
     */
    public Timestamp agora() {
        Object bruto = frequenciaRepository.dataHoraServidor();
        if (bruto == null) {
            return Timestamp.valueOf(java.time.LocalDateTime.now());
        }
        if (bruto instanceof Timestamp timestamp) {
            return timestamp;
        }
        if (bruto instanceof java.time.OffsetDateTime odt) {
            return Timestamp.from(odt.toInstant());
        }
        if (bruto instanceof java.time.ZonedDateTime zdt) {
            return Timestamp.from(zdt.toInstant());
        }
        if (bruto instanceof java.time.LocalDateTime ldt) {
            return Timestamp.valueOf(ldt);
        }
        if (bruto instanceof java.util.Date d) {
            return new Timestamp(d.getTime());
        }
        return Timestamp.valueOf(java.time.LocalDateTime.now());
    }

    /** Data de hoje segundo o banco. */
    public LocalDate hoje() {
        return agora().toLocalDateTime().toLocalDate();
    }

    /**
     * Resumo de frequência do aluno (mesmo contrato devolvido por
     * {@code GET /api/frequencia/resumo/{alunoId}} e usado no detalhe do aluno).
     */
    public Map<String, Object> resumo(Long alunoId) {
        LocalDate hoje = hoje();
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

    /** Dias úteis (segunda a sexta), inclusive nas duas pontas. */
    public long contarDiasUteis(LocalDate inicio, LocalDate fim) {
        long total = 0;
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            switch (d.getDayOfWeek()) {
                case SATURDAY, SUNDAY -> { }
                default -> total++;
            }
        }
        return total;
    }

    private Map<String, Object> resumirRegistro(Frequencia f) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", f.getId());
        m.put("data", f.getData());
        m.put("horaEntrada", f.getHoraEntrada());
        return m;
    }
}