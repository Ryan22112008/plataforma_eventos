package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.repository.PresencaRepository;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.service.InscricaoService;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InscricaoServiceValidacaoTest {

    private static final Long EVENTO_ID = 10L;
    private static final Long OUTRO_EVENTO_ID = 20L;
    private static final Long INSCRICAO_ID = 101L;

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
    void organizadorValidaParticipanteDoProprioEvento() {
        Inscricao inscricao = inscricao(EVENTO_ID, StatusInscricao.ATIVO);
        when(inscricaoRepository.findById(INSCRICAO_ID)).thenReturn(Optional.of(inscricao));

        Inscricao resultado = inscricaoService.validarParticipacaoDoParticipante(EVENTO_ID, INSCRICAO_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusInscricao.UTILIZADO);
        assertThat(resultado.getDataCheckIn()).isNotNull();
        verify(inscricaoRepository).save(inscricao);
    }

    @Test
    void naoValidaInscricaoDeOutroEvento() {
        Inscricao inscricao = inscricao(OUTRO_EVENTO_ID, StatusInscricao.ATIVO);
        when(inscricaoRepository.findById(INSCRICAO_ID)).thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() -> inscricaoService.validarParticipacaoDoParticipante(EVENTO_ID, INSCRICAO_ID))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(inscricao.getStatus()).isEqualTo(StatusInscricao.ATIVO);
        verify(inscricaoRepository, never()).save(inscricao);
    }

    @Test
    void rejeitaInscricaoInexistente() {
        when(inscricaoRepository.findById(INSCRICAO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inscricaoService.validarParticipacaoDoParticipante(EVENTO_ID, INSCRICAO_ID))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("não encontrada");
    }

    @Test
    void rejeitaInscricaoCancelada() {
        Inscricao inscricao = inscricao(EVENTO_ID, StatusInscricao.CANCELADO);
        when(inscricaoRepository.findById(INSCRICAO_ID)).thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() -> inscricaoService.validarParticipacaoDoParticipante(EVENTO_ID, INSCRICAO_ID))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("cancelada");

        assertThat(inscricao.getStatus()).isEqualTo(StatusInscricao.CANCELADO);
        verify(inscricaoRepository, never()).save(inscricao);
    }

    @Test
    void rejeitaInscricaoSemParticipanteVinculado() {
        Inscricao inscricao = inscricao(EVENTO_ID, StatusInscricao.ATIVO);
        inscricao.setParticipante(null);
        when(inscricaoRepository.findById(INSCRICAO_ID)).thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() -> inscricaoService.validarParticipacaoDoParticipante(EVENTO_ID, INSCRICAO_ID))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("participante");
    }

    @Test
    void validacaoEhIdempotenteParaInscricaoJaUtilizada() {
        Inscricao inscricao = inscricao(EVENTO_ID, StatusInscricao.UTILIZADO);
        when(inscricaoRepository.findById(INSCRICAO_ID)).thenReturn(Optional.of(inscricao));

        Inscricao resultado = inscricaoService.validarParticipacaoDoParticipante(EVENTO_ID, INSCRICAO_ID);

        assertThat(resultado.getStatus()).isEqualTo(StatusInscricao.UTILIZADO);
        verify(inscricaoRepository, never()).save(inscricao);
    }

    @Test
    void loteSemSelecionadosNaoConsultaBanco() {
        assertThat(inscricaoService.validarParticipacoesEmLote(EVENTO_ID, List.of())).isZero();
        verify(inscricaoRepository, never()).findAllByIdComRelacoes(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void loteValidaUmaInscricaoEPreencheDataCheckIn() {
        Inscricao inscricao = inscricao(EVENTO_ID, StatusInscricao.ATIVO);
        when(inscricaoRepository.findAllByIdComRelacoes(List.of(INSCRICAO_ID))).thenReturn(List.of(inscricao));

        assertThat(inscricaoService.validarParticipacoesEmLote(EVENTO_ID, List.of(INSCRICAO_ID))).isEqualTo(1);

        assertThat(inscricao.getStatus()).isEqualTo(StatusInscricao.UTILIZADO);
        assertThat(inscricao.getDataCheckIn()).isNotNull();
        verify(inscricaoRepository).saveAll(List.of(inscricao));
    }

    @Test
    void loteValidaMultiplasInscricoesEmUmaPersistencia() {
        Inscricao primeira = inscricao(EVENTO_ID, StatusInscricao.ATIVO);
        Inscricao segunda = inscricao(EVENTO_ID, StatusInscricao.ATIVO);
        segunda.setId(102L);
        when(inscricaoRepository.findAllByIdComRelacoes(List.of(INSCRICAO_ID, 102L)))
                .thenReturn(List.of(primeira, segunda));

        assertThat(inscricaoService.validarParticipacoesEmLote(EVENTO_ID, List.of(INSCRICAO_ID, 102L)))
                .isEqualTo(2);
        verify(inscricaoRepository).saveAll(List.of(primeira, segunda));
    }

    @Test
    void loteIgnoraJaUtilizadaCanceladaOutroEventoEParticipanteInconsistente() {
        Inscricao utilizada = inscricao(EVENTO_ID, StatusInscricao.UTILIZADO);
        Inscricao cancelada = inscricao(EVENTO_ID, StatusInscricao.CANCELADO);
        Inscricao deOutroEvento = inscricao(OUTRO_EVENTO_ID, StatusInscricao.ATIVO);
        Inscricao semParticipante = inscricao(EVENTO_ID, StatusInscricao.ATIVO);
        semParticipante.setParticipante(null);
        when(inscricaoRepository.findAllByIdComRelacoes(List.of(1L, 2L, 3L, 4L)))
                .thenReturn(List.of(utilizada, cancelada, deOutroEvento, semParticipante));

        assertThat(inscricaoService.validarParticipacoesEmLote(EVENTO_ID, List.of(1L, 2L, 3L, 4L))).isZero();
        verify(inscricaoRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void loteEhIdempotenteEPreservaDataCheckInJaExistente() {
        Inscricao inscricao = inscricao(EVENTO_ID, StatusInscricao.ATIVO);
        java.time.LocalDateTime checkIn = java.time.LocalDateTime.of(2026, 1, 2, 3, 4);
        inscricao.setDataCheckIn(checkIn);
        when(inscricaoRepository.findAllByIdComRelacoes(List.of(INSCRICAO_ID)))
                .thenReturn(List.of(inscricao));

        assertThat(inscricaoService.validarParticipacoesEmLote(EVENTO_ID, List.of(INSCRICAO_ID))).isEqualTo(1);
        assertThat(inscricao.getDataCheckIn()).isEqualTo(checkIn);
        when(inscricaoRepository.findAllByIdComRelacoes(List.of(INSCRICAO_ID)))
                .thenReturn(List.of(inscricao));
        assertThat(inscricaoService.validarParticipacoesEmLote(EVENTO_ID, List.of(INSCRICAO_ID))).isZero();
    }

    private Inscricao inscricao(Long eventoId, StatusInscricao status) {
        Evento evento = new Evento();
        evento.setId(eventoId);

        Usuario participante = new Usuario();
        participante.setId(1L);
        participante.setNome("Participante");
        participante.setEmail("participante@seplag.mt.gov.br");

        return Inscricao.builder()
                .id(INSCRICAO_ID)
                .evento(evento)
                .participante(participante)
                .status(status)
                .build();
    }
}
