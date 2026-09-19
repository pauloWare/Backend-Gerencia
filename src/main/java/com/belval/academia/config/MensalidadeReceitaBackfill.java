package com.belval.academia.config;

import com.belval.academia.model.Mensalidade;
import com.belval.academia.model.Receita;
import com.belval.academia.repository.MensalidadeRepository;
import com.belval.academia.repository.ReceitaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Backfill (idempotente) que vincula as Receitas legadas à sua Mensalidade de origem.
 *
 * Antes da coluna mensalidade_id existir, as Receitas criadas por POST /api/mensalidade/{id}/pagar
 * não tinham como identificar a Mensalidade relacionada de forma confiável. Esta rotina roda uma
 * única vez por dado (na subida da aplicação) e, apenas quando encontra uma CORRESPONDÊNCIA ÚNICA
 * (mesmo aluno + mesma data de pagamento + tipo MENSALIDADE + receita ainda sem vínculo), grava o
 * mensalidadeId — sem duplicar e sem adivinhar relações ambíguas.
 */
@Component
public class MensalidadeReceitaBackfill implements CommandLineRunner {

    @Autowired
    private MensalidadeRepository mensalidadeRepository;

    @Autowired
    private ReceitaRepository receitaRepository;

    @Override
    public void run(String... args) {
        List<Mensalidade> pagas = mensalidadeRepository.findAll().stream()
                .filter(m -> ("PAGA".equals(m.getStatus()) || "PAGO".equals(m.getStatus())) && m.getId() != null)
                .toList();

        for (Mensalidade m : pagas) {
            boolean jaVinculada = receitaRepository.findByMensalidadeId(m.getId()).stream()
                    .anyMatch(r -> r.getMensalidadeId() != null);
            if (jaVinculada) continue;

            List<Receita> candidatas = receitaRepository.findAll().stream()
                    .filter(r -> "MENSALIDADE".equals(r.getTipo()))
                    .filter(r -> r.getMensalidadeId() == null)
                    .filter(r -> r.getAluno() != null && m.getAluno() != null
                            && r.getAluno().getId().equals(m.getAluno().getId()))
                    .filter(r -> r.getData() != null && r.getData().equals(m.getDataPagamento()))
                    .toList();

            if (candidatas.size() == 1) {
                Receita receita = candidatas.get(0);
                receita.setMensalidadeId(m.getId());
                receitaRepository.save(receita);
            }
        }
    }
}