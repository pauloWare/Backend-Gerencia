package com.belval.academia.controller;

import com.belval.academia.model.Receita;
import com.belval.academia.model.Despesa;
import com.belval.academia.model.Mensalidade;
import com.belval.academia.repository.ReceitaRepository;
import com.belval.academia.repository.DespesaRepository;
import com.belval.academia.repository.MensalidadeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@RestController
@RequestMapping("/api/financeiro")
@CrossOrigin(origins = "http://localhost:5173")
public class FinanceiroController {

    @Autowired
    private ReceitaRepository receitaRepository;

    @Autowired
    private DespesaRepository despesaRepository;

    @Autowired
    private MensalidadeRepository mensalidadeRepository;

    private static final String[] MESES = {
        "Jan","Fev","Mar","Abr","Mai","Jun","Jul","Ago","Set","Out","Nov","Dez"
    };

    // ===== RECEITAS =====
    @GetMapping("/receitas")
    public List<Receita> listarReceitas() {
        return receitaRepository.findAll();
    }

    @PostMapping("/receitas")
    public Receita criarReceita(@RequestBody Receita r) {
        return receitaRepository.save(r);
    }

    @PutMapping("/receitas/{id}")
    public ResponseEntity<Receita> atualizarReceita(@PathVariable Long id, @RequestBody Receita r) {
        return receitaRepository.findById(id)
                .map(existing -> { r.setId(id); return ResponseEntity.ok(receitaRepository.save(r)); })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/receitas/{id}")
    public ResponseEntity<Void> deletarReceita(@PathVariable Long id) {
        return receitaRepository.findById(id)
                .map(existing -> { receitaRepository.deleteById(id); return ResponseEntity.ok().<Void>build(); })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===== DESPESAS =====
    @GetMapping("/despesas")
    public List<Despesa> listarDespesas() {
        return despesaRepository.findAll();
    }

    @PostMapping("/despesas")
    public Despesa criarDespesa(@RequestBody Despesa d) {
        return despesaRepository.save(d);
    }

    @PutMapping("/despesas/{id}")
    public ResponseEntity<Despesa> atualizarDespesa(@PathVariable Long id, @RequestBody Despesa d) {
        return despesaRepository.findById(id)
                .map(existing -> { d.setId(id); return ResponseEntity.ok(despesaRepository.save(d)); })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/despesas/{id}")
    public ResponseEntity<Void> deletarDespesa(@PathVariable Long id) {
        return despesaRepository.findById(id)
                .map(existing -> { despesaRepository.deleteById(id); return ResponseEntity.ok().<Void>build(); })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===== RESUMO MENSAL =====
    @GetMapping("/resumo")
    public ResumoFinanceiro resumo(@RequestParam String inicio, @RequestParam String fim) {
        LocalDate i = LocalDate.parse(inicio);
        LocalDate f = LocalDate.parse(fim);
        double totalReceitas = receitaRepository.findByDataBetween(i, f).stream().mapToDouble(Receita::getValor).sum();
        double totalDespesas = despesaRepository.findByDataBetween(i, f).stream().mapToDouble(Despesa::getValor).sum();
        return new ResumoFinanceiro(totalReceitas, totalDespesas, totalReceitas - totalDespesas);
    }

    // ===== INDICADORES FINANCEIROS =====
    @GetMapping("/indicadores")
    public Map<String, Object> indicadores() {
        // Receita recebida: apenas pagamentos efetivamente registrados
        double receitaRecebida = receitaRepository.findAll().stream()
                .mapToDouble(r -> r.getValor() == null ? 0 : r.getValor()).sum();

        // A receber: somatório das mensalidades ainda em aberto
        double aReceber = mensalidadeRepository.findAll().stream()
                .filter(m -> !("PAGA".equals(m.getStatus()) || "PAGO".equals(m.getStatus()) || "CANCELADO".equals(m.getStatus())))
                .mapToDouble(m -> m.getValor() == null ? 0 : m.getValor()).sum();

        List<Despesa> despesas = despesaRepository.findAll();
        double despesasFixas = despesas.stream()
                .filter(d -> "FIXO".equals(d.getClassificacao()))
                .mapToDouble(d -> d.getValor() == null ? 0 : d.getValor()).sum();
        double despesasVariaveis = despesas.stream()
                .filter(d -> "VARIAVEL".equals(d.getClassificacao()))
                .mapToDouble(d -> d.getValor() == null ? 0 : d.getValor()).sum();
        double despesasTotais = despesas.stream()
                .mapToDouble(d -> d.getValor() == null ? 0 : d.getValor()).sum();
        double saldo = receitaRecebida - despesasTotais;

        Map<String, Object> r = new HashMap<>();
        r.put("receitaRecebida", receitaRecebida);
        r.put("aReceber", aReceber);
        r.put("despesasFixas", despesasFixas);
        r.put("despesasVariaveis", despesasVariaveis);
        r.put("despesasTotais", despesasTotais);
        r.put("saldo", saldo);
        return r;
    }

    // ===== HISTÓRICO FINANCEIRO POR MÊS =====
    @GetMapping("/historico")
    public List<Map<String, Object>> historico(@RequestParam(defaultValue = "12") int meses) {
        Map<YearMonth, double[]> porMes = new TreeMap<>();
        receitaRepository.findAll().forEach(r -> {
            if (r.getData() != null) {
                YearMonth ym = YearMonth.from(r.getData());
                double[] v = porMes.computeIfAbsent(ym, k -> new double[2]);
                v[0] += r.getValor() == null ? 0 : r.getValor();
            }
        });
        despesaRepository.findAll().forEach(d -> {
            if (d.getData() != null) {
                YearMonth ym = YearMonth.from(d.getData());
                double[] v = porMes.computeIfAbsent(ym, k -> new double[2]);
                v[1] += d.getValor() == null ? 0 : d.getValor();
            }
        });

        YearMonth limite = YearMonth.now().minusMonths(Math.max(0, meses - 1L));
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Map.Entry<YearMonth, double[]> e : porMes.entrySet()) {
            YearMonth ym = e.getKey();
            if (ym.isBefore(limite) || ym.isAfter(YearMonth.now())) continue;
            double[] v = e.getValue();
            Map<String, Object> item = new HashMap<>();
            item.put("referencia", ym.toString());
            item.put("rotulo", MESES[ym.getMonthValue() - 1] + "/" + ym.getYear());
            item.put("receitas", Math.round(v[0] * 100.0) / 100.0);
            item.put("despesas", Math.round(v[1] * 100.0) / 100.0);
            item.put("saldo", Math.round((v[0] - v[1]) * 100.0) / 100.0);
            lista.add(item);
        }
        Collections.reverse(lista);
        return lista;
    }

    /**
     * Histórico de faturamento detalhado por mês.
     * Reutiliza Receita e Despesa (dados reais), sem criar tabelas novas.
     * Retorna a evolução mensal (receitas, despesas, saldo) com quebra por
     * origem (mensalidades x outras) e natureza da despesa (fixa/variável/manutenção),
     * além de indicadores do período.
     */
    @GetMapping("/faturamento/historico")
    public Map<String, Object> faturamentoHistorico(
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim) {
        LocalDate dataInicio = (inicio != null && !inicio.isBlank()) ? LocalDate.parse(inicio) : null;
        LocalDate dataFim = (fim != null && !fim.isBlank()) ? LocalDate.parse(fim) : null;

        Map<YearMonth, Map<String, Object>> porMes = new TreeMap<>();

        receitaRepository.findAll().forEach(r -> {
            if (r.getData() == null) return;
            if (dataInicio != null && r.getData().isBefore(dataInicio)) return;
            if (dataFim != null && r.getData().isAfter(dataFim)) return;
            YearMonth ym = YearMonth.from(r.getData());
            Map<String, Object> m = porMes.computeIfAbsent(ym, k -> novoMesFaturamento());
            double v = r.getValor() == null ? 0 : r.getValor();
            m.put("receitas", (double) m.get("receitas") + v);
            if ("MENSALIDADE".equals(r.getTipo())) {
                m.put("receitaMensalidade", (double) m.get("receitaMensalidade") + v);
            } else {
                m.put("outrasReceitas", (double) m.get("outrasReceitas") + v);
            }
        });

        despesaRepository.findAll().forEach(d -> {
            if (d.getData() == null) return;
            if (dataInicio != null && d.getData().isBefore(dataInicio)) return;
            if (dataFim != null && d.getData().isAfter(dataFim)) return;
            YearMonth ym = YearMonth.from(d.getData());
            Map<String, Object> m = porMes.computeIfAbsent(ym, k -> novoMesFaturamento());
            double v = d.getValor() == null ? 0 : d.getValor();
            m.put("despesas", (double) m.get("despesas") + v);
            if ("FIXO".equals(d.getClassificacao())) {
                m.put("despesasFixas", (double) m.get("despesasFixas") + v);
            } else {
                m.put("despesasVariaveis", (double) m.get("despesasVariaveis") + v);
            }
            if ("MANUTENCAO".equals(d.getTipo()) || d.getManutencaoId() != null) {
                m.put("despesasManutencao", (double) m.get("despesasManutencao") + v);
            }
        });

        List<Map<String, Object>> mesesLista = new ArrayList<>();
        double somaReceitas = 0, somaDespesas = 0;
        for (Map.Entry<YearMonth, Map<String, Object>> e : porMes.entrySet()) {
            Map<String, Object> m = e.getValue();
            double rec = (double) m.get("receitas");
            double des = (double) m.get("despesas");
            m.put("referencia", e.getKey().toString());
            m.put("rotulo", MESES[e.getKey().getMonthValue() - 1] + "/" + e.getKey().getYear());
            m.put("receitas", Math.round(rec * 100.0) / 100.0);
            m.put("despesas", Math.round(des * 100.0) / 100.0);
            m.put("saldo", Math.round((rec - des) * 100.0) / 100.0);
            m.put("resultadoLiquido", Math.round((rec - des) * 100.0) / 100.0);
            m.put("receitaMensalidade", Math.round((double) m.get("receitaMensalidade") * 100.0) / 100.0);
            m.put("outrasReceitas", Math.round((double) m.get("outrasReceitas") * 100.0) / 100.0);
            m.put("despesasFixas", Math.round((double) m.get("despesasFixas") * 100.0) / 100.0);
            m.put("despesasVariaveis", Math.round((double) m.get("despesasVariaveis") * 100.0) / 100.0);
            m.put("despesasManutencao", Math.round((double) m.get("despesasManutencao") * 100.0) / 100.0);
            somaReceitas += rec;
            somaDespesas += des;
            mesesLista.add(m);
        }
        Collections.reverse(mesesLista); // mais recente primeiro

        int qtdMeses = porMes.size();
        Map<String, Object> indicadores = new HashMap<>();
        indicadores.put("faturamento", Math.round(somaReceitas * 100.0) / 100.0);
        indicadores.put("totalRecebido", Math.round(somaReceitas * 100.0) / 100.0);
        indicadores.put("totalDespesas", Math.round(somaDespesas * 100.0) / 100.0);
        indicadores.put("saldo", Math.round((somaReceitas - somaDespesas) * 100.0) / 100.0);
        indicadores.put("mediaMensal", qtdMeses > 0 ? Math.round((somaReceitas / qtdMeses) * 100.0) / 100.0 : 0);
        indicadores.put("mediaDespesaMensal", qtdMeses > 0 ? Math.round((somaDespesas / qtdMeses) * 100.0) / 100.0 : 0);

        Map<String, Object> r = new HashMap<>();
        r.put("meses", mesesLista);
        r.put("indicadores", indicadores);
        return r;
    }

    private Map<String, Object> novoMesFaturamento() {
        Map<String, Object> m = new HashMap<>();
        m.put("receitas", 0.0);
        m.put("despesas", 0.0);
        m.put("receitaMensalidade", 0.0);
        m.put("outrasReceitas", 0.0);
        m.put("despesasFixas", 0.0);
        m.put("despesasVariaveis", 0.0);
        m.put("despesasManutencao", 0.0);
        return m;
    }

    // ===== ESTIMATIVA DE CENÁRIOS =====
    @GetMapping("/estimativa")
    public Map<String, Object> estimativa(@RequestParam(defaultValue = "6") int meses) {
        Map<YearMonth, double[]> porMes = new TreeMap<>();
        receitaRepository.findAll().forEach(r -> {
            if (r.getData() != null) {
                YearMonth ym = YearMonth.from(r.getData());
                double[] v = porMes.computeIfAbsent(ym, k -> new double[2]);
                v[0] += r.getValor() == null ? 0 : r.getValor();
            }
        });
        despesaRepository.findAll().forEach(d -> {
            if (d.getData() != null) {
                YearMonth ym = YearMonth.from(d.getData());
                double[] v = porMes.computeIfAbsent(ym, k -> new double[2]);
                v[1] += d.getValor() == null ? 0 : d.getValor();
            }
        });

        YearMonth limite = YearMonth.now().minusMonths(Math.max(0, meses - 1L));
        List<String> referencias = new ArrayList<>();
        double somaReceita = 0, somaDespesa = 0;
        int count = 0;
        for (Map.Entry<YearMonth, double[]> e : porMes.entrySet()) {
            YearMonth ym = e.getKey();
            if (ym.isBefore(limite) || ym.isAfter(YearMonth.now())) continue;
            referencias.add(ym.toString());
            somaReceita += e.getValue()[0];
            somaDespesa += e.getValue()[1];
            count++;
        }
        double mediaReceita = count > 0 ? somaReceita / count : 0;
        double mediaDespesa = count > 0 ? somaDespesa / count : 0;

        Map<String, Object> r = new HashMap<>();
        r.put("mesesAnalisados", referencias);
        r.put("mediaReceita", Math.round(mediaReceita * 100.0) / 100.0);
        r.put("mediaDespesa", Math.round(mediaDespesa * 100.0) / 100.0);
        if (count > 0) {
            r.put("cenarios", List.of(
                cenario("Conservador", mediaReceita * 0.85, mediaDespesa * 0.85),
                cenario("Esperado", mediaReceita, mediaDespesa),
                cenario("Otimista", mediaReceita * 1.15, mediaDespesa * 1.15)
            ));
        } else {
            r.put("cenarios", List.of());
        }
        r.put("nota", "Estimativa baseada no histórico financeiro. Não representa uma previsão garantida.");
        return r;
    }

    private Map<String, Object> cenario(String nome, double receita, double despesa) {
        Map<String, Object> m = new HashMap<>();
        m.put("nome", nome);
        m.put("receita", Math.round(receita * 100.0) / 100.0);
        m.put("despesa", Math.round(despesa * 100.0) / 100.0);
        m.put("saldo", Math.round((receita - despesa) * 100.0) / 100.0);
        return m;
    }

    // ===== DESPESA VINDA DE MANUTENÇÃO (integração) =====
    // RODADA 3: a descrição digitada na manutenção é salva como-is na Despesa
    // (mesmo registro financeiro exibido em /financeiro). Sem prefixo, para que
    // o valor seja único e consistente nos dois contextos. Despesas antigas
    // (com prefixo "Manutenção - ...") continuam intactas.
    @PostMapping("/despesas/manutencao")
    public ResponseEntity<Despesa> criarDespesaManutencao(@RequestBody DespesaManutencaoRequest req) {
        Despesa d = new Despesa();
        d.setTipo("MANUTENCAO");
        d.setClassificacao("VARIAVEL");
        String informada = req.getDescricao() != null ? req.getDescricao().trim() : "";
        if (!informada.isEmpty()) {
            d.setDescricao(informada);
        } else {
            String equip = req.getEquipamento();
            d.setDescricao((equip != null && !equip.isBlank())
                    ? "Manutenção - " + equip + " - Serviço"
                    : "Manutenção - Serviço");
        }
        d.setValor(req.getValor());
        d.setData(req.getData() != null ? req.getData() : LocalDate.now());
        d.setManutencaoId(req.getManutencaoId());
        return ResponseEntity.ok(despesaRepository.save(d));
    }

    public static class DespesaManutencaoRequest {
        private String descricao;
        private String equipamento;
        private Double valor;
        private LocalDate data;
        private Long manutencaoId;

        public String getDescricao() { return descricao; }
        public void setDescricao(String descricao) { this.descricao = descricao; }
        public String getEquipamento() { return equipamento; }
        public void setEquipamento(String equipamento) { this.equipamento = equipamento; }
        public Double getValor() { return valor; }
        public void setValor(Double valor) { this.valor = valor; }
        public LocalDate getData() { return data; }
        public void setData(LocalDate data) { this.data = data; }
        public Long getManutencaoId() { return manutencaoId; }
        public void setManutencaoId(Long manutencaoId) { this.manutencaoId = manutencaoId; }
    }

    public static class ResumoFinanceiro {
        public double totalReceitas;
        public double totalDespesas;
        public double saldo;

        public ResumoFinanceiro(double totalReceitas, double totalDespesas, double saldo) {
            this.totalReceitas = totalReceitas;
            this.totalDespesas = totalDespesas;
            this.saldo = saldo;
        }
    }
}
