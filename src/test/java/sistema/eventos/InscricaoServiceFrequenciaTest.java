package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sistema.eventos.dto.InscritoFrequenciaDto;
import sistema.eventos.dto.PresencaPorAlunoProjection;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.StatusPresenca;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.repository.PresencaRepository;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.service.InscricaoService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InscricaoServiceFrequenciaTest {

    private static final Long EVENTO_ID = 10L;
    private static final Long PARTICIPANTE_A = 1L;
    private static final Long PARTICIPANTE_B = 2L;
    private static final Long PARTICIPANTE_C = 3L;

    @Mock
    private InscricaoRepository inscricaoRepository;

    @Mock
    private EventoRepository eventoRepository;

    @Mock
    private SessaoRepository sessaoRepository;

    @Mock
    private PresencaRepository presencaRepository;

    @InjectMocks
    private InscricaoService inscricaoService;

    @Test
    void calculaFrequenciaDeCadaParticipanteComVariasSessoes() {
        Inscricao inscricaoA = inscricao(101L, PARTICIPANTE_A, "Ana");
        Inscricao inscricaoB = inscricao(102L, PARTICIPANTE_B, "Bruno");
        Inscricao inscricaoC = inscricao(103L, PARTICIPANTE_C, "Carla");

        PresencaPorAlunoProjection projecaoA = projecao(PARTICIPANTE_A, 3L);
        PresencaPorAlunoProjection projecaoB = projecao(PARTICIPANTE_B, 4L);

        when(inscricaoRepository.findByEventoId(EVENTO_ID))
                .thenReturn(List.of(inscricaoA, inscricaoB, inscricaoC));
        when(sessaoRepository.countByEventoId(EVENTO_ID)).thenReturn(4L);
        when(presencaRepository.contarPresencasPresentesPorAluno(EVENTO_ID, StatusPresenca.PRESENTE))
                .thenReturn(List.of(projecaoA, projecaoB));

        List<InscritoFrequenciaDto> resultado = inscricaoService.listarInscritosComFrequencia(EVENTO_ID);

        assertThat(resultado).hasSize(3);

        InscritoFrequenciaDto ana = porParticipante(resultado, PARTICIPANTE_A);
        assertThat(ana.getPresencasPresentes()).isEqualTo(3L);
        assertThat(ana.getTotalSessoes()).isEqualTo(4L);
        assertThat(ana.getPercentualFrequencia()).isEqualTo(75.0);
        assertThat(ana.getFrequenciaFormatada()).isEqualTo("75%");
        assertThat(ana.isCertificadoLiberado()).isFalse();

        InscritoFrequenciaDto bruno = porParticipante(resultado, PARTICIPANTE_B);
        assertThat(bruno.getPresencasPresentes()).isEqualTo(4L);
        assertThat(bruno.getPercentualFrequencia()).isEqualTo(100.0);
        assertThat(bruno.getFrequenciaFormatada()).isEqualTo("100%");

        InscritoFrequenciaDto carla = porParticipante(resultado, PARTICIPANTE_C);
        assertThat(carla.getPresencasPresentes()).isZero();
        assertThat(carla.getPercentualFrequencia()).isZero();
    }

    @Test
    void consultaAgregadaUsaSomenteStatusPresente() {
        PresencaPorAlunoProjection projecaoA = projecao(PARTICIPANTE_A, 1L);

        when(inscricaoRepository.findByEventoId(EVENTO_ID))
                .thenReturn(List.of(inscricao(101L, PARTICIPANTE_A, "Ana")));
        when(sessaoRepository.countByEventoId(EVENTO_ID)).thenReturn(2L);
        when(presencaRepository.contarPresencasPresentesPorAluno(EVENTO_ID, StatusPresenca.PRESENTE))
                .thenReturn(List.of(projecaoA));

        inscricaoService.listarInscritosComFrequencia(EVENTO_ID);

        verify(presencaRepository)
                .contarPresencasPresentesPorAluno(EVENTO_ID, StatusPresenca.PRESENTE);
    }

    @Test
    void eventoSemSessoesNaoDividePorZeroEMostraFrequenciaNaoAplicavel() {
        when(inscricaoRepository.findByEventoId(EVENTO_ID))
                .thenReturn(List.of(inscricao(101L, PARTICIPANTE_A, "Ana")));
        when(sessaoRepository.countByEventoId(EVENTO_ID)).thenReturn(0L);
        when(presencaRepository.contarPresencasPresentesPorAluno(EVENTO_ID, StatusPresenca.PRESENTE))
                .thenReturn(List.of());

        List<InscritoFrequenciaDto> resultado = inscricaoService.listarInscritosComFrequencia(EVENTO_ID);

        assertThat(resultado).hasSize(1);
        InscritoFrequenciaDto ana = resultado.getFirst();
        assertThat(ana.getTotalSessoes()).isZero();
        assertThat(ana.getPresencasPresentes()).isZero();
        assertThat(ana.getPercentualFrequencia()).isNull();
        assertThat(ana.getFrequenciaFormatada()).isNull();
    }

    @Test
    void inscricaoUtilizadaApareceComCertificadoLiberado() {
        Inscricao inscricao = inscricao(101L, PARTICIPANTE_A, "Ana");
        inscricao.setStatus(StatusInscricao.UTILIZADO);

        PresencaPorAlunoProjection projecaoA = projecao(PARTICIPANTE_A, 2L);

        when(inscricaoRepository.findByEventoId(EVENTO_ID)).thenReturn(List.of(inscricao));
        when(sessaoRepository.countByEventoId(EVENTO_ID)).thenReturn(2L);
        when(presencaRepository.contarPresencasPresentesPorAluno(EVENTO_ID, StatusPresenca.PRESENTE))
                .thenReturn(List.of(projecaoA));

        InscritoFrequenciaDto resultado = inscricaoService
                .listarInscritosComFrequencia(EVENTO_ID)
                .getFirst();

        assertThat(resultado.isCertificadoLiberado()).isTrue();
        assertThat(resultado.isInscricaoCancelada()).isFalse();
    }

    private Inscricao inscricao(Long inscricaoId, Long participanteId, String nome) {
        Evento evento = new Evento();
        evento.setId(EVENTO_ID);

        Usuario participante = new Usuario();
        participante.setId(participanteId);
        participante.setNome(nome);
        participante.setEmail(nome.toLowerCase() + "@seplag.mt.gov.br");

        return Inscricao.builder()
                .id(inscricaoId)
                .participante(participante)
                .evento(evento)
                .status(StatusInscricao.ATIVO)
                .build();
    }

    private PresencaPorAlunoProjection projecao(Long alunoId, Long totalPresentes) {
        PresencaPorAlunoProjection projecao = mock(PresencaPorAlunoProjection.class);
        when(projecao.getAlunoId()).thenReturn(alunoId);
        when(projecao.getTotalPresentes()).thenReturn(totalPresentes);
        return projecao;
    }

    private InscritoFrequenciaDto porParticipante(List<InscritoFrequenciaDto> resultado, Long participanteId) {
        return resultado.stream()
                .filter(item -> participanteId.equals(item.getParticipanteId()))
                .findFirst()
                .orElseThrow();
    }
}
