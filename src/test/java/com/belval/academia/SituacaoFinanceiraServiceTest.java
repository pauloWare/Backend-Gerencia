package com.belval.academia;

import com.belval.academia.exception.ErroDeRegraDeNegocio;
import com.belval.academia.model.Mensalidade;
import com.belval.academia.repository.MensalidadeRepository;
import com.belval.academia.service.SituacaoFinanceiraService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Regra de "mensalidade atrasada" usada no controle de acesso do check-in e na
 * consulta financeira do app.
 *
 * <p>O ponto crítico é o <b>fail-closed</b>: se a consulta financeira falhar, o
 * aluno NÃO pode ser liberado (uma falha nunca resulta em autorização indevida).</p>
 */
@ExtendWith(MockitoExtension.class)
class SituacaoFinanceiraServiceTest {

    @Mock
    private MensalidadeRepository mensalidadeRepository;

    private SituacaoFinanceiraService servico;

    private final LocalDate hoje = LocalDate.of(2026, 6, 15);

    @BeforeEach
    void setUp() {
        servico = new SituacaoFinanceiraService(mensalidadeRepository);
    }

    private Mensalidade mensalidade(String status, LocalDate vencimento) {
        Mensalidade m = new Mensalidade();
        m.setId(1L);
        m.setStatus(status);
        m.setDataVencimento(vencimento);
        m.setValor(100.0);
        return m;
    }

    @Test
    void mensalidadeVencidaENaoPagaEhAtrasadaEBloqueia() {
        when(mensalidadeRepository.findByAlunoId(1L))
                .thenReturn(List.of(mensalidade("PENDENTE", hoje.minusDays(3))));

        assertThat(servico.situacao(1L, hoje)).isEqualTo(SituacaoFinanceiraService.ATRASADA);
        assertThat(servico.acessoBloqueadoPorMensalidade(1L, hoje)).isTrue();
    }

    @Test
    void mensalidadePagaNaoBloqueiaMesmoComVencimentoPassado() {
        when(mensalidadeRepository.findByAlunoId(1L))
                .thenReturn(List.of(mensalidade("PAGA", hoje.minusDays(30))));

        assertThat(servico.situacao(1L, hoje)).isEqualTo(SituacaoFinanceiraService.EM_DIA);
        assertThat(servico.acessoBloqueadoPorMensalidade(1L, hoje)).isFalse();
    }

    @Test
    void mensalidadePendenteDentroDoPrazoNaoBloqueia() {
        when(mensalidadeRepository.findByAlunoId(1L))
                .thenReturn(List.of(mensalidade("PENDENTE", hoje.plusDays(10))));

        assertThat(servico.situacao(1L, hoje)).isEqualTo(SituacaoFinanceiraService.EM_DIA);
        assertThat(servico.acessoBloqueadoPorMensalidade(1L, hoje)).isFalse();
    }

    @Test
    void alunoSemMensalidadeNaoEhTratadoComoEmDia() {
        when(mensalidadeRepository.findByAlunoId(1L)).thenReturn(List.of());

        // Sem mensalidade não há bloqueio, mas a situação NÃO é "EM_DIA"
        // (a interface precisa poder dizer "sem mensalidade cadastrada").
        assertThat(servico.situacao(1L, hoje)).isEqualTo(SituacaoFinanceiraService.SEM_MENSALIDADE);
        assertThat(servico.acessoBloqueadoPorMensalidade(1L, hoje)).isFalse();
    }

    @Test
    void falhaNaConsultaFinanceiraNaoAutoriza() {
        when(mensalidadeRepository.findByAlunoId(1L))
                .thenThrow(new DataAccessResourceFailureException("banco indisponível"));

        assertThatThrownBy(() -> servico.acessoBloqueadoPorMensalidade(1L, hoje))
                .isInstanceOf(ErroDeRegraDeNegocio.class)
                .satisfies(erro -> assertThat(((ErroDeRegraDeNegocio) erro).getStatus())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void quandoHaAtrasoEntreVariasMensalidadesASituacaoEhAtrasada() {
        when(mensalidadeRepository.findByAlunoId(1L)).thenReturn(List.of(
                mensalidade("PAGA", hoje.minusDays(60)),
                mensalidade("PENDENTE", hoje.minusDays(1)),
                mensalidade("PENDENTE", hoje.plusDays(20))));

        assertThat(servico.situacao(1L, hoje)).isEqualTo(SituacaoFinanceiraService.ATRASADA);
        assertThat(servico.primeiraEmAtraso(1L, hoje)).isPresent();
    }
}