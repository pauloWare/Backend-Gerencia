package com.belval.academia.controller;

import com.belval.academia.model.Equipamento;
import com.belval.academia.model.Chamado;
import com.belval.academia.model.ManutencaoPreventiva;
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
    public Equipamento criarEquipamento(@RequestBody Equipamento e) {
        return equipamentoRepository.save(e);
    }

    @PutMapping("/equipamentos/{id}")
    public ResponseEntity<Equipamento> atualizarEquipamento(@PathVariable Long id, @RequestBody Equipamento e) {
        return equipamentoRepository.findById(id)
                .map(existing -> { e.setId(id); return ResponseEntity.ok(equipamentoRepository.save(e)); })
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
