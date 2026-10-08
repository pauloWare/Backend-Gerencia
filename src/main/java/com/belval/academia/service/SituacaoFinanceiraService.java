package com.belval.academia.service;

import com.belval.academia.exception.ErroDeRegraDeNegocio;
import com.belval.academia.model.Mensalidade;
import com.belval.academia.repository.MensalidadeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Situação financeira do ALUNO (mensalidades).
 *
 * <p>Este serviço concentra a MESMA regra de "mensalidade atrasada" usada pelo
 * sistema web (tela Mensalidades):</p>
 * <ul>
 *   <li>{@code PAGA}/{@code PAGO} → quitada;</li>
 *   <li>não quitada com {@code dataVencimento} anterior a hoje → ATRASADA;</li>
 *   <li>demais casos → PENDENTE (ainda dentro do prazo).</li>
 * </ul>
 *
 * <p>É usado em dois pontos: (1) controle de acesso do check-in (mensalidade
 * atrasada impede o registro de presença) e (2) consulta do próprio aluno no
 * app. A regra é aplicada SEMPRE no backend — o aplicativo nunca decide sozinho
 * se o aluno pode entrar.</p>
 *
 * <p><b>Fail-closed:</b> se a leitura das mensalidades falhar, é lançado um erro
 * ({@code 503}) em vez de liberar o acesso. Uma falha na consulta financeira
 * nunca resulta em autorização indevida.</p>
 */
@Service
public class SituacaoFinanceiraService {

    private static final Logger log = LoggerFactory.getLogger(SituacaoFinanceiraService.class);

    /** Situações financeiras possíveis do aluno. */
    public static final String EM_DIA = "EM_DIA";
    public static final String ATRASADA = "ATRASADA";
    public static final String SEM_MENSALIDADE = "SEM_MENSALIDADE";

    /** Situações de exibição de uma mensalidade (mesmos rótulos do web). */
    public static final String PAGA = "PAGA";
    public static final String PENDENTE = "PENDENTE";

    private final MensalidadeRepository mensalidadeRepository;

    public SituacaoFinanceiraService(MensalidadeRepository mensalidadeRepository) {
        this.mensalidadeRepository = mensalidadeRepository;
    }

    /**
     * Mensalidades do aluno (mais recentes primeiro). Lança {@code 503} quando a
     * consulta não pode ser concluída — o chamador trata como bloqueio.
     */
    public List<Mensalidade> listar(Long alunoId) {
        if (alunoId == null) {
            throw new ErroDeRegraDeNegocio(HttpStatus.BAD_REQUEST, "Aluno não identificado.");
        }
        try {
            List<Mensalidade> encontradas = mensalidadeRepository.findByAlunoId(alunoId);
            if (encontradas == null) {
                return List.of();
            }
            return encontradas.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(
                            Mensalidade::getDataVencimento,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } catch (DataAccessException e) {
            log.error("Falha ao consultar as mensalidades do aluno id={}", alunoId, e);
            throw new ErroDeRegraDeNegocio(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível validar sua situação financeira agora. Tente novamente em instantes.");
        }
    }

    /** Uma mensalidade está quitada quando o status é PAGA/PAGO. */
    public static boolean estaPaga(Mensalidade mensalidade) {
        if (mensalidade == null || mensalidade.getStatus() == null) {
            return false;
        }
        String status = mensalidade.getStatus().trim();
        return "PAGA".equalsIgnoreCase(status) || "PAGO".equalsIgnoreCase(status);
    }

    /** Rótulo de exibição da mensalidade (idêntico ao usado pelo frontend web). */
    public static String situacaoExibicao(Mensalidade mensalidade, LocalDate hoje) {
        if (estaPaga(mensalidade)) {
            return PAGA;
        }
        if (mensalidade != null && mensalidade.getDataVencimento() != null
                && hoje != null && mensalidade.getDataVencimento().isBefore(hoje)) {
            return ATRASADA;
        }
        return PENDENTE;
    }

    /** Primeira mensalidade atrasada do aluno (vazio quando não há). */
    public Optional<Mensalidade> primeiraEmAtraso(Long alunoId, LocalDate hoje) {
        return listar(alunoId).stream()
                .filter(m -> ATRASADA.equals(situacaoExibicao(m, hoje)))
                .findFirst();
    }

    /**
     * Situação financeira consolidada do aluno. Usada para o controle de acesso
     * e para a tela de mensalidade do app.
     */
    public String situacao(Long alunoId, LocalDate hoje) {
        List<Mensalidade> mensalidades = listar(alunoId);
        if (mensalidades.isEmpty()) {
            return SEM_MENSALIDADE;
        }
        boolean temAtraso = mensalidades.stream()
                .anyMatch(m -> ATRASADA.equals(situacaoExibicao(m, hoje)));
        return temAtraso ? ATRASADA : EM_DIA;
    }

    /**
     * Regra de acesso da academia: mensalidade atrasada bloqueia o registro de
     * presença. Aluno sem mensalidade cadastrada não é bloqueado (não há dívida
     * registrada) — a decisão segue 100% o backend.
     */
    public boolean acessoBloqueadoPorMensalidade(Long alunoId, LocalDate hoje) {
        return ATRASADA.equals(situacao(alunoId, hoje));
    }
}