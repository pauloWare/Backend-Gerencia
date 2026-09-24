package com.belval.academia.controller;

import com.belval.academia.model.Equipamento;
import com.belval.academia.model.Chamado;
import com.belval.academia.model.ManutencaoPreventiva;
import com.belval.academia.model.SlaPolicy;
import com.belval.academia.repository.EquipamentoRepository;
import com.belval.academia.repository.ChamadoRepository;
import com.belval.academia.repository.ManutencaoPreventivaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/manutencao")
@CrossOrigin(origins = "http://localhost:5173")
public class ManutencaoController {

    @Autowired
    private EquipamentoRepository equipamentoRepository;

    @Autowired
    private ChamadoRepository chamadoRepository;

    @Autowired
    private ManutencaoPreventivaRepository preventivaRepository;

    // ===== EQUIPAMENTOS (endpoints usados pelo frontend) =====
    @GetMapping
    public List<Equipamento> listarEquipamentosRaiz() {
        return equipamentoRepository.findAll();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarEquipamentoRaiz(@PathVariable Long id) {
        return equipamentoRepository.findById(id)
                .map(existing -> { equipamentoRepository.deleteById(id); return ResponseEntity.ok().<Void>build(); })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===== EQUIPAMENTOS =====
    @GetMapping("/equipamentos")
    public List<Equipamento> listarEquipamentos() {
        return equipamentoRepository.findAll();
    }

    @PostMapping("/equipamentos")
    public ResponseEntity<?> criarEquipamento(@RequestBody Equipamento e) {
        // RODADA 5: identificação (nome) é gerada pelo sistema via
        // /equipamentos/proxima-identificacao. Aqui só garantimos que não
        // sejam criadas duas unidades com a mesma identificação.
        String nome = e.getNome() != null ? e.getNome().trim() : "";
        if (nome.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Informe o tipo do equipamento para gerar a identificação."));
        }
        synchronized (equipamentoRepository) {
            boolean duplicado = equipamentoRepository.findAll().stream()
                    .anyMatch(x -> x.getNome() != null && x.getNome().trim().equalsIgnoreCase(nome));
            if (duplicado) {
                return ResponseEntity.status(409).body(Map.of(
                        "erro", "Já existe um equipamento com a identificação \"" + nome + "\".",
                        "campo", "nome"));
            }
            e.setNome(nome);
            return ResponseEntity.ok(equipamentoRepository.save(e));
        }
    }

    /**
     * RODADA 5 — Geração automática da identificação do equipamento.
     *
     * Dado o tipo (ex.: "Esteira"), procura as identificações existentes
     * daquele tipo ("Esteira", "Esteira 01", "Esteira 02", ...) e devolve a
     * próxima ("Esteira 04"). A numeração é por tipo, não global. O cálculo
     * é feito no servidor para não depender de contador no frontend.
     */
    @GetMapping("/equipamentos/proxima-identificacao")
    public ResponseEntity<?> proximaIdentificacao(@RequestParam String tipo) {
        String base = tipo != null ? tipo.trim().replaceAll("\\s+", " ") : "";
        // Preserva a capitalização digitada, mas usa uma forma normalizada
        // para comparar com os existentes (case-insensitive nos helpers).
        if (base.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Informe o tipo do equipamento."));
        }
        synchronized (equipamentoRepository) {
            int maior = 0;
            boolean existeDoTipo = false;
            for (Equipamento eq : equipamentoRepository.findAll()) {
                String nome = eq.getNome() != null ? eq.getNome().trim() : "";
                int numero = numeroDaIdentificacao(nome, base);
                if (numero >= 0) {
                    existeDoTipo = true;
                    if (numero > maior) {
                        maior = numero;
                    }
                }
            }
            // Nenhum do tipo ainda: começa em 01. "Esteira" puro conta como 0,
            // então a próxima vira "Esteira 01".
            int proximo = existeDoTipo ? maior + 1 : 1;
            String identificacao = base + " " + String.format("%02d", proximo);
            return ResponseEntity.ok(Map.of("tipo", base, "identificacao", identificacao));
        }
    }

    /**
     * Se o nome pertence ao tipo informado, devolve o número da unidade:
     * "Esteira" -> 0, "Esteira 01" -> 1. Caso contrário, -1.
     */
    private static int numeroDaIdentificacao(String nome, String tipoBase) {
        if (nome.equalsIgnoreCase(tipoBase)) {
            return 0;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(.*?)\\s+0*(\\d+)\\s*$")
                .matcher(nome);
        if (m.matches() && m.group(1).trim().equalsIgnoreCase(tipoBase)) {
            try {
                return Integer.parseInt(m.group(2));
            } catch (NumberFormatException ex) {
                return -1;
            }
        }
        return -1;
    }

    @PutMapping("/equipamentos/{id}")
    public ResponseEntity<?> atualizarEquipamento(@PathVariable Long id, @RequestBody Equipamento e) {
        return equipamentoRepository.findById(id)
                .map(existing -> {
                    // RODADA 5: a identificação existente é preservada — a edição
                    // não renomeia a unidade (evita quebrar histórico). Se o nome
                    // vier diferente, só aceita se não colidir com outro registro.
                    String nome = e.getNome() != null && !e.getNome().isBlank()
                            ? e.getNome().trim()
                            : existing.getNome();
                    boolean colide = equipamentoRepository.findAll().stream()
                            .anyMatch(x -> !x.getId().equals(id)
                                    && x.getNome() != null
                                    && x.getNome().trim().equalsIgnoreCase(nome));
                    if (colide) {
                        return ResponseEntity.status(409).body(Map.of(
                                "erro", "Já existe outro equipamento com a identificação \"" + nome + "\".",
                                "campo", "nome"));
                    }
                    e.setId(id);
                    e.setNome(nome);
                    return ResponseEntity.ok(equipamentoRepository.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/equipamentos/{id}")
    public ResponseEntity<Void> deletarEquipamento(@PathVariable Long id) {
        return equipamentoRepository.findById(id)
                .map(existing -> { equipamentoRepository.deleteById(id); return ResponseEntity.ok().<Void>build(); })
                .orElse(ResponseEntity.notFound().build());
    }

    // ===== CHAMADOS (manutenção corretiva + SLA) =====
    @GetMapping("/chamados")
    public List<Chamado> listarChamados() {
        return chamadoRepository.findAll();
    }

    @PostMapping("/chamados")
    public Chamado criarChamado(@RequestBody Chamado c) {
        // RODADA 1 — SLA DE MANUTENÇÃO: o prazo é sempre calculado pelo
        // sistema a partir da prioridade. Qualquer valor enviado pelo
        // frontend é ignorado. Chamados históricos não são afetados aqui
        // (apenas novos chamados passam por este endpoint).
        if (c.getPrioridade() == null || c.getPrioridade().isBlank()) {
            c.setPrioridade(SlaPolicy.prioridadePadrao());
        }
        c.setSlaDias(SlaPolicy.slaDiasParaPrioridade(c.getPrioridade()));
        return chamadoRepository.save(c);
    }

    @GetMapping("/chamados/status/{status}")
    public List<Chamado> porStatus(@PathVariable String status) {
        return chamadoRepository.findByStatus(status);
    }

    @PutMapping("/chamados/{id}")
    public ResponseEntity<Chamado> atualizarChamado(@PathVariable Long id, @RequestBody Chamado c) {
        return chamadoRepository.findById(id)
                .map(existing -> {
                    c.setId(id);
                    // RODADA 1 — SLA DE MANUTENÇÃO: o técnico/nem a recepção
                    // podem arbitrar o SLA. Regras:
                    // - se a prioridade foi alterada, o SLA é recalculado
                    //   pelo sistema a partir da nova prioridade;
                    // - se a prioridade NÃO mudou, o SLA histórico é
                    //   preservado (qualquer slaDias enviado é ignorado).
                    boolean prioridadeMudou = c.getPrioridade() != null
                            && !c.getPrioridade().isBlank()
                            && (existing.getPrioridade() == null
                                || !c.getPrioridade().equalsIgnoreCase(existing.getPrioridade()));
                    if (prioridadeMudou) {
                        Integer recalculado = SlaPolicy.slaDiasParaPrioridade(c.getPrioridade());
                        c.setSlaDias(recalculado != null ? recalculado : existing.getSlaDias());
                    } else {
                        c.setPrioridade(existing.getPrioridade());
                        c.setSlaDias(existing.getSlaDias());
                    }
                    if ("FINALIZADO".equals(c.getStatus()) && c.getDataConclusao() == null) {
                        c.setDataConclusao(LocalDate.now());
                    }
                    return ResponseEntity.ok(chamadoRepository.save(c));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/chamados/{id}")
    public ResponseEntity<Void> deletarChamado(@PathVariable Long id) {
        return chamadoRepository.findById(id)
                .map(existing -> { chamadoRepository.deleteById(id); return ResponseEntity.ok().<Void>build(); })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Histórico completo de chamados (inclui finalizados/encerrados).
     * Reutiliza os registros existentes de Chamado, sem duplicação de tabela.
     * Filtros opcionais aplicados no servidor; os indicadores refletem o total geral.
     */
    @GetMapping("/historico")
    public Map<String, Object> historico(
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tecnico,
            @RequestParam(required = false) Long equipamento,
            @RequestParam(required = false) String prioridade,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String slaStatus) {
        List<Chamado> todos = chamadoRepository.findAll();

        LocalDate i = (inicio != null && !inicio.isBlank()) ? LocalDate.parse(inicio) : null;
        LocalDate f = (fim != null && !fim.isBlank()) ? LocalDate.parse(fim) : null;

        List<Chamado> filtrados = todos.stream()
                .filter(c -> i == null || (c.getData() != null && !c.getData().isBefore(i)))
                .filter(c -> f == null || (c.getData() != null && !c.getData().isAfter(f)))
                .filter(c -> status == null || status.isBlank() || status.equalsIgnoreCase(c.getStatus()))
                .filter(c -> tecnico == null || tecnico.isBlank()
                        || (c.getResponsavel() != null && c.getResponsavel().toLowerCase().contains(tecnico.toLowerCase())))
                .filter(c -> equipamento == null
                        || (c.getEquipamento() != null && equipamento.equals(c.getEquipamento().getId())))
                .filter(c -> prioridade == null || prioridade.isBlank() || prioridade.equalsIgnoreCase(c.getPrioridade()))
                .filter(c -> tipo == null || tipo.isBlank() || tipo.equalsIgnoreCase(c.getTipo()))
                .filter(c -> slaStatus == null || slaStatus.isBlank() || slaStatus.equalsIgnoreCase(c.getSlaStatus()))
                .sorted(Comparator
                        .comparing(Chamado::getData, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Chamado::getId, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        Map<String, Object> indicadores = new HashMap<>();
        indicadores.put("total", (long) todos.size());
        indicadores.put("concluidos", todos.stream().filter(c -> "FINALIZADO".equals(c.getStatus())).count());
        indicadores.put("emAndamento", todos.stream().filter(c -> !"FINALIZADO".equals(c.getStatus())).count());
        indicadores.put("dentroSla", todos.stream()
                .filter(c -> "DENTRO_PRAZO".equals(c.getSlaStatus()) || "CONCLUIDO_DENTRO_SLA".equals(c.getSlaStatus()))
                .count());
        indicadores.put("foraSla", todos.stream()
                .filter(c -> "SLA_VENCIDO".equals(c.getSlaStatus()) || "CONCLUIDO_APOS_SLA".equals(c.getSlaStatus()))
                .count());
        indicadores.put("vencidos", todos.stream().filter(c -> "SLA_VENCIDO".equals(c.getSlaStatus())).count());

        Map<String, Object> r = new HashMap<>();
        r.put("chamados", filtrados);
        r.put("indicadores", indicadores);
        return r;
    }

    // ===== MANUTENÇÕES PREVENTIVAS =====
    @GetMapping("/preventivas")
    public List<ManutencaoPreventiva> listarPreventivas() {
        return preventivaRepository.findAll();
    }

    @PostMapping("/preventivas")
    public ManutencaoPreventiva criarPreventiva(@RequestBody ManutencaoPreventiva p) {
        calcularProxima(p);
        if (p.getStatus() == null) {
            p.setStatus("AGENDADA");
        }
        return preventivaRepository.save(p);
    }

    @PutMapping("/preventivas/{id}")
    public ResponseEntity<ManutencaoPreventiva> atualizarPreventiva(@PathVariable Long id, @RequestBody ManutencaoPreventiva p) {
        return preventivaRepository.findById(id)
                .map(existing -> {
                    p.setId(id);
                    calcularProxima(p);
                    return ResponseEntity.ok(preventivaRepository.save(p));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/preventivas/{id}")
    public ResponseEntity<Void> deletarPreventiva(@PathVariable Long id) {
        return preventivaRepository.findById(id)
                .map(existing -> { preventivaRepository.deleteById(id); return ResponseEntity.ok().<Void>build(); })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Se a próxima manutenção não foi informada, calcula a partir da
     * última manutenção + periodicidade.
     */
    private void calcularProxima(ManutencaoPreventiva p) {
        if (p.getProximaManutencao() == null
                && p.getDataUltimaManutencao() != null
                && p.getPeriodicidade() != null) {
            p.setProximaManutencao(p.getDataUltimaManutencao().plusDays(p.getPeriodicidade()));
        }
    }
}
