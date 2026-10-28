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
