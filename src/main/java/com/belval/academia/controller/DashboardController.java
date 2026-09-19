package com.belval.academia.controller;

import com.belval.academia.model.Frequencia;
import com.belval.academia.model.Mensalidade;
import com.belval.academia.repository.AlunoRepository;
import com.belval.academia.repository.MensalidadeRepository;
import com.belval.academia.repository.FrequenciaRepository;
import com.belval.academia.repository.ReceitaRepository;
import com.belval.academia.repository.DespesaRepository;
import com.belval.academia.repository.EquipamentoRepository;
import com.belval.academia.repository.ChamadoRepository;
import com.belval.academia.repository.ManutencaoPreventivaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "http://localhost:5173")
public class DashboardController {

    /** Limite (percentual) usado para sinalizar baixa frequência no mês corrente. */
    private static final double LIMITE_BAIXA_FREQUENCIA = 70.0;

    @Autowired private AlunoRepository alunoRepository;
    @Autowired private MensalidadeRepository mensalidadeRepository;
    @Autowired private FrequenciaRepository frequenciaRepository;
    @Autowired private ReceitaRepository receitaRepository;
    @Autowired private DespesaRepository despesaRepository;
    @Autowired private EquipamentoRepository equipamentoRepository;
    @Autowired private ChamadoRepository chamadoRepository;
    @Autowired private ManutencaoPreventivaRepository preventivaRepository;

    @GetMapping
    public Map<String, Object> indicadores() {
        LocalDate hoje = LocalDate.now();
        YearMonth mesAtual = YearMonth.now();
        LocalDate inicioMes = mesAtual.atDay(1);
        LocalDate fimMes = mesAtual.atEndOfMonth();

        long totalAlunos = alunoRepository.count();
        long alunosAtivos = alunoRepository.findAll().stream()
                .filter(a -> "ATIVO".equals(a.getSituacao()))
                .count();

        List<Mensalidade> todasMensalidades = mensalidadeRepository.findAll();
        long mensalidadesPendentes = todasMensalidades.stream()
                .filter(m -> !("PAGA".equals(m.getStatus()) || "PAGO".equals(m.getStatus()) || "CANCELADO".equals(m.getStatus())))
                .count();
        long alunosInadimplentes = todasMensalidades.stream()
                .filter(m -> !"PAGA".equals(m.getStatus())
                        && m.getDataVencimento() != null
                        && m.getDataVencimento().isBefore(hoje))
                .count();
        long mensalidadesAVencer = todasMensalidades.stream()
                .filter(m -> m.getDataVencimento() != null
                        && !"PAGA".equals(m.getStatus())
                        && !m.getDataVencimento().isBefore(hoje)
                        && m.getDataVencimento().isBefore(hoje.plusDays(7)))
                .count();
        double aReceber = todasMensalidades.stream()
                .filter(m -> !("PAGA".equals(m.getStatus()) || "PAGO".equals(m.getStatus()) || "CANCELADO".equals(m.getStatus())))
                .mapToDouble(m -> m.getValor() == null ? 0 : m.getValor())
                .sum();
        double receitaMes = receitaRepository.findByDataBetween(inicioMes, fimMes).stream()
                .mapToDouble(r -> r.getValor() == null ? 0 : r.getValor()).sum();
        List<com.belval.academia.model.Despesa> despesasMes = despesaRepository.findByDataBetween(inicioMes, fimMes);
        double despesaMes = despesasMes.stream()
                .mapToDouble(d -> d.getValor() == null ? 0 : d.getValor()).sum();
        double saldoMes = receitaMes - despesaMes;
        double despesasFixasMes = despesasMes.stream()
                .filter(d -> "FIXO".equals(d.getClassificacao()))
                .mapToDouble(d -> d.getValor() == null ? 0 : d.getValor()).sum();
        double despesasVariaveisMes = despesasMes.stream()
                .filter(d -> "VARIAVEL".equals(d.getClassificacao()))
                .mapToDouble(d -> d.getValor() == null ? 0 : d.getValor()).sum();
        long equipamentosManutencao = equipamentoRepository.findAll().stream()
                .filter(e -> "MANUTENCAO".equals(e.getSituacao()))
                .count();
        long chamadosAbertos = chamadoRepository.findByStatus("ABERTO").size();
        long chamadosAndamento = chamadoRepository.findByStatus("EM_ANDAMENTO").size();
        // Manutenções pendentes: chamados não finalizados + preventivas não concluídas
        long chamadosPendentes = chamadoRepository.findAll().stream()
                .filter(c -> !"FINALIZADO".equals(c.getStatus()))
                .count();
        long preventivasPendentes = preventivaRepository.findAll().stream()
                .filter(p -> !"CONCLUIDA".equals(p.getStatusExibicao()))
                .count();
        long manutencoesPendentes = chamadosPendentes + preventivasPendentes;
        List<Frequencia> todasFrequencias = frequenciaRepository.findAll();
        // Presenças de hoje contabilizando cada aluno UMA única vez
        // (protege a métrica contra eventuais registros duplicados antigos)
        long presencasHoje = todasFrequencias.stream()
                .filter(f -> f.getData() != null && f.getData().equals(hoje) && f.getAluno() != null)
                .map(f -> f.getAluno().getId())
                .distinct()
                .count();
        long frequenciaDiaria = presencasHoje;
        long frequenciaHoje = alunosAtivos > 0
                ? Math.round((presencasHoje * 100.0) / alunosAtivos)
                : 0;
        long frequenciaMensal = todasFrequencias.stream()
                .filter(f -> f.getData() != null && !f.getData().isBefore(inicioMes) && !f.getData().isAfter(fimMes))
                .count();

        // Alunos ativos com frequência abaixo do limite no mês corrente.
        // Mesmo critério do resumo por aluno: dias úteis decorridos do mês.
        long diasUteisDecorridos = contarDiasUteis(inicioMes, hoje);
        long alunosBaixaFrequencia = 0;
        if (diasUteisDecorridos > 0) {
            for (com.belval.academia.model.Aluno a : alunoRepository.findAll()) {
                if (!"ATIVO".equals(a.getSituacao())) continue;
                long presencas = todasFrequencias.stream()
                        .filter(f -> f.getAluno() != null && a.getId().equals(f.getAluno().getId()))
                        .filter(f -> f.getData() != null && !f.getData().isBefore(inicioMes) && !f.getData().isAfter(hoje))
                        .map(Frequencia::getData)
                        .distinct()
                        .count();
                if ((presencas * 100.0) / diasUteisDecorridos < LIMITE_BAIXA_FREQUENCIA) {
                    alunosBaixaFrequencia++;
                }
            }
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("totalAlunos", totalAlunos);
        result.put("alunosAtivos", alunosAtivos);
        result.put("alunosInadimplentes", alunosInadimplentes);
        result.put("mensalidadesPendentes", mensalidadesPendentes);
        result.put("mensalidadesAVencer", mensalidadesAVencer);
        result.put("aReceber", aReceber);
        result.put("receitaMes", receitaMes);
        result.put("despesaMes", despesaMes);
        result.put("saldoMes", saldoMes);
        result.put("despesasFixasMes", despesasFixasMes);
        result.put("despesasVariaveisMes", despesasVariaveisMes);
        result.put("equipamentosManutencao", equipamentosManutencao);
        result.put("chamadosAbertos", chamadosAbertos);
        result.put("chamadosAndamento", chamadosAndamento);
        result.put("manutencoesPendentes", manutencoesPendentes);
        result.put("frequenciaHoje", frequenciaHoje);
        result.put("frequenciaDiaria", frequenciaDiaria);
        result.put("frequenciaMensal", frequenciaMensal);
        result.put("presencasHoje", presencasHoje);
        result.put("alunosBaixaFrequencia", alunosBaixaFrequencia);
        return result;
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
}
