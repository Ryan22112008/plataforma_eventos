package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.StatusEvento;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.repository.PresencaRepository;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.repository.SorteioResultadoRepository;
import sistema.eventos.service.InscricaoService;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InscricaoPrivacidadeFluxoTest {

    @Mock private InscricaoRepository inscricaoRepository;
    @Mock private EventoRepository eventoRepository;
    @Mock private SessaoRepository sessaoRepository;
    @Mock private PresencaRepository presencaRepository;
    @Mock private SorteioResultadoRepository sorteioResultadoRepository;

    @Test
    void eventoPublicoReservaVagaEConfirmaInscricao() {
        assertFluxo(false, StatusInscricao.ATIVO, 4);
    }

    @Test
    void eventoPrivadoCriaSolicitacaoSemReservarVaga() {
        assertFluxo(true, StatusInscricao.PENDENTE, 5);
    }

    @Test
    void eventoComPrivacidadeNulaSegueFluxoPublicoSemAlterarRegistroAntesDoCadastro() {
        assertFluxo(null, StatusInscricao.ATIVO, 4);
    }

    private void assertFluxo(Boolean privado, StatusInscricao esperado, int vagasEsperadas) {
        InscricaoService service = new InscricaoService(inscricaoRepository, eventoRepository,
                sessaoRepository, presencaRepository, sorteioResultadoRepository);
        Evento evento = new Evento();
        evento.setId(7L);
        evento.setPrivado(privado);
        evento.setStatus(StatusEvento.PUBLICADO);
        evento.setDataTermino(LocalDate.now().plusDays(30));
        evento.setVagasTotais(5);
        evento.setVagasRestantes(5);
        Usuario participante = new Usuario();
        participante.setId(9L);

        when(eventoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));
        when(inscricaoRepository.findByParticipanteIdAndEventoIdComEvento(9L, 7L)).thenReturn(Optional.empty());
        when(inscricaoRepository.save(any(Inscricao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StatusInscricao resultado = service.inscreverUsuario(7L, participante);

        assertThat(resultado).isEqualTo(esperado);
        assertThat(evento.getVagasRestantes()).isEqualTo(vagasEsperadas);
        verify(inscricaoRepository).save(any(Inscricao.class));
    }
}
