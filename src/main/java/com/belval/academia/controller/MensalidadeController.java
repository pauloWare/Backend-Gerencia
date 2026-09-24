package com.belval.academia.controller;

import com.belval.academia.model.Mensalidade;
import com.belval.academia.model.Receita;
import com.belval.academia.repository.MensalidadeRepository;
import com.belval.academia.repository.ReceitaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/mensalidade")
@CrossOrigin(origins = {"http://localhost:5173", "https://gerencia-sigma.vercel.app", "https://gerencia-gsdy7lqhm-paulowares-projects.vercel.app"})
public class MensalidadeController {

    @Autowired
    private MensalidadeRepository mensalidadeRepository;

    @Autowired
    private ReceitaRepository receitaRepository;

    @GetMapping
    public List<Mensalidade> listar() {
        return mensalidadeRepository.findAll();
    }

    @PostMapping
    public Mensalidade criar(@RequestBody Mensalidade mensalidade) {
        return mensalidadeRepository.save(mensalidade);
    }

    @GetMapping("/aluno/{alunoId}")
    public List<Mensalidade> porAluno(@PathVariable Long alunoId) {
        return mensalidadeRepository.findByAlunoId(alunoId);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Mensalidade> atualizar(@PathVariable Long id, @RequestBody Mensalidade m) {
        return mensalidadeRepository.findById(id)
                .map(existing -> {
                    // Preserva relacionamentos/dados que o frontend não envia no corpo da edição
                    if (m.getAluno() == null) m.setAluno(existing.getAluno());
                    if (m.getUsuarioResponsavel() == null) m.setUsuarioResponsavel(existing.getUsuarioResponsavel());
                    if (m.getDataPagamento() == null) m.setDataPagamento(existing.getDataPagamento());
                    m.setId(id);
                    Mensalidade salva = mensalidadeRepository.save(m);
                    // Mantém a Receita financeira sincronizada quando a mensalidade é PAGA.
                    sincronizarReceita(salva);
                    return ResponseEntity.ok(salva);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Atualiza a Receita que representa o pagamento desta mensalidade SEM criar duplicidade.
     * A Receita vinculada é identificada pelo campo mensalidadeId, gravado no fluxo de pagamento
     * (POST /{id}/pagar). O ID da Receita é preservado (UPDATE, nunca INSERT).
     * Caso a mensalidade não possua Receita vinculada (registros legados sem vínculo), nada é
     * criado nem adivinhado: apenas a mensalidade é salva, protegendo a integridade financeira.
     */
    private void sincronizarReceita(Mensalidade m) {
        if (m.getId() == null) return;
        List<Receita> vinculadas = receitaRepository.findByMensalidadeId(m.getId());
        for (Receita receita : vinculadas) {
            if (m.getValor() != null) receita.setValor(m.getValor());
            String nome = m.getAluno() != null ? m.getAluno().getNome() : "Aluno";
            receita.setDescricao("Mensalidade - " + nome);
            if (m.getDataPagamento() != null) receita.setData(m.getDataPagamento());
            receitaRepository.save(receita);
        }
    }

    /**
     * Registra o pagamento de uma mensalidade (fluxo acadêmico/fictício).
     * A mensalidade passa para PAGA e é criado um registro financeiro (Receita)
     * que alimenta a receita real da Dashboard e do Financeiro.
     */
    @PostMapping("/{id}/pagar")
    public ResponseEntity<?> registrarPagamento(@PathVariable Long id, @RequestBody PagamentoRequest req) {
        return mensalidadeRepository.findById(id)
                .map(existing -> {
                    double valorPago = req.getValor() != null ? req.getValor() : (existing.getValor() != null ? existing.getValor() : 0);
                    LocalDate dataPagamento = req.getDataPagamento() != null ? req.getDataPagamento() : LocalDate.now();

                    existing.setStatus("PAGA");
                    existing.setDataPagamento(dataPagamento);
                    existing.setValor(valorPago);
                    existing.setFormaPagamento(req.getFormaPagamento());
                    existing.setUsuarioResponsavel(req.getUsuarioResponsavel());
                    Mensalidade salva = mensalidadeRepository.save(existing);

                    // Registro financeiro real do pagamento
                    Receita receita = new Receita();
                    receita.setTipo("MENSALIDADE");
                    receita.setDescricao("Mensalidade - " + (existing.getAluno() != null ? existing.getAluno().getNome() : "Aluno"));
                    receita.setValor(valorPago);
                    receita.setData(dataPagamento);
                    receita.setAluno(existing.getAluno());
                    receita.setMensalidadeId(salva.getId());
                    receitaRepository.save(receita);

                    return ResponseEntity.ok(salva);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        return mensalidadeRepository.findById(id)
                .map(existing -> { mensalidadeRepository.deleteById(id); return ResponseEntity.ok().<Void>build(); })
                .orElse(ResponseEntity.notFound().build());
    }

    public static class PagamentoRequest {
        private Double valor;
        private LocalDate dataPagamento;
        private String formaPagamento;
        private String usuarioResponsavel;

        public Double getValor() { return valor; }
        public void setValor(Double valor) { this.valor = valor; }
        public LocalDate getDataPagamento() { return dataPagamento; }
        public void setDataPagamento(LocalDate dataPagamento) { this.dataPagamento = dataPagamento; }
        public String getFormaPagamento() { return formaPagamento; }
        public void setFormaPagamento(String formaPagamento) { this.formaPagamento = formaPagamento; }
        public String getUsuarioResponsavel() { return usuarioResponsavel; }
        public void setUsuarioResponsavel(String usuarioResponsavel) { this.usuarioResponsavel = usuarioResponsavel; }
    }
}
