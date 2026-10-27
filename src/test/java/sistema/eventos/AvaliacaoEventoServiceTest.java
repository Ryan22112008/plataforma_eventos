package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sistema.eventos.dto.AvaliacaoEventoRequestDto;
import sistema.eventos.dto.AvaliacaoResumoDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.AvaliacaoEvento;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.AvaliacaoEventoRepository;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.PresencaRepository;
import sistema.eventos.service.AvaliacaoEventoService;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvaliacaoEventoServiceTest {

    @Mock
    private AvaliacaoEventoRepository avaliacaoRepository;

    @Mock
    private PresencaRepository presencaRepository;

    @Mock
    private EventoRepository eventoRepository;

    @InjectMocks
    private AvaliacaoEventoService avaliacaoService;

    private Evento evento(Long eventoId, Long organizadorId) {
        Evento evento = new Evento();
        evento.setId(eventoId);
        evento.setTitulo("Evento Teste");
        Usuario organizador = new Usuario();
        organizador.setId(organizadorId);
        evento.setOrganizador(organizador);
        return evento;
    }

    private Usuario usuario(Long id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome("Participante Teste");
        return u;
    }

    private AvaliacaoEventoRequestDto dto(Integer nota) {
        AvaliacaoEventoRequestDto dto = new AvaliacaoEventoRequestDto();
        dto.setNota(nota);
        dto.setComentario("Comentario de teste.");
        return dto;
    }

    @Test
    void deveCriarAvaliacaoQuandoUsuarioParticipou() {
        Long eventoId = 10L;
        Long usuarioId = 1L;
        Long organizadorId = 2L;
        Evento evento = evento(eventoId, organizadorId);
        Usuario usuario = usuario(usuarioId);

        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));
        when(presencaRepository.countPresencasConfirmadasPorAluno(usuarioId, eventoId)).thenReturn(1L);
        when(avaliacaoRepository.existsByEventoIdAndUsuarioId(eventoId, usuarioId)).thenReturn(false);
        when(avaliacaoRepository.save(any(AvaliacaoEvento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AvaliacaoEvento resultado = avaliacaoService.criar(eventoId, usuario, dto(5));

        assertEquals(Integer.valueOf(5), resultado.getNota());
        assertEquals(usuario, resultado.getUsuario());
        assertEquals(evento, resultado.getEvento());
        verify(avaliacaoRepository).save(any(AvaliacaoEvento.class));
    }

    @Test
    void naoDeveCriarAvaliacaoQuandoUsuarioNaoParticipou() {
        Long eventoId = 10L;
        Long usuarioId = 1L;
        Long organizadorId = 2L;
        Evento evento = evento(eventoId, organizadorId);
        Usuario usuario = usuario(usuarioId);

        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));
        when(presencaRepository.countPresencasConfirmadasPorAluno(usuarioId, eventoId)).thenReturn(0L);

        NegocioException ex = assertThrows(NegocioException.class,
                () -> avaliacaoService.criar(eventoId, usuario, dto(5)));
        assertTrue(ex.getMessage().contains("participou"));
        verify(avaliacaoRepository, never()).save(any());
    }

    @Test
    void naoDeveCriarAvaliacaoQuandoUsuarioEhOrganizador() {
        Long eventoId = 10L;
        Long usuarioId = 1L;
        Evento evento = evento(eventoId, usuarioId);
        Usuario usuario = usuario(usuarioId);

        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> avaliacaoService.criar(eventoId, usuario, dto(5)));
        assertTrue(ex.getMessage().contains("organizador"));
        verify(avaliacaoRepository, never()).save(any());
    }

    @Test
    void naoDeveCriarAvaliacaoDuplicada() {
        Long eventoId = 10L;
        Long usuarioId = 1L;
        Long organizadorId = 2L;
        Evento evento = evento(eventoId, organizadorId);
        Usuario usuario = usuario(usuarioId);

        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));
        when(presencaRepository.countPresencasConfirmadasPorAluno(usuarioId, eventoId)).thenReturn(1L);
        when(avaliacaoRepository.existsByEventoIdAndUsuarioId(eventoId, usuarioId)).thenReturn(true);

        NegocioException ex = assertThrows(NegocioException.class,
                () -> avaliacaoService.criar(eventoId, usuario, dto(5)));
        assertTrue(ex.getMessage().contains("j\u00e1 avaliou"));
        verify(avaliacaoRepository, never()).save(any());
    }

    @Test
    void naoDeveCriarAvaliacaoComNotaInvalida() {
        Long eventoId = 10L;
        Long usuarioId = 1L;
        Long organizadorId = 2L;
        Evento evento = evento(eventoId, organizadorId);
        Usuario usuario = usuario(usuarioId);

        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));
        when(presencaRepository.countPresencasConfirmadasPorAluno(usuarioId, eventoId)).thenReturn(1L);
        when(avaliacaoRepository.existsByEventoIdAndUsuarioId(eventoId, usuarioId)).thenReturn(false);

        assertThrows(NegocioException.class, () -> avaliacaoService.criar(eventoId, usuario, dto(0)));
        assertThrows(NegocioException.class, () -> avaliacaoService.criar(eventoId, usuario, dto(6)));
        assertThrows(NegocioException.class, () -> avaliacaoService.criar(eventoId, usuario, dto(null)));
        verify(avaliacaoRepository, never()).save(any());
    }

    @Test
    void naoDeveAtualizarAvaliacaoQueNaoEhPropria() {
        Long eventoId = 10L;
        Long usuarioId = 1L;
        when(avaliacaoRepository.findByEventoIdAndUsuarioId(eventoId, usuarioId)).thenReturn(Optional.empty());

        NegocioException ex = assertThrows(NegocioException.class,
                () -> avaliacaoService.atualizar(eventoId, usuario(usuarioId), dto(4)));
        assertTrue(ex.getMessage().contains("n\u00e3o possui avalia\u00e7\u00e3o"));
        verify(avaliacaoRepository, never()).save(any());
    }

    @Test
    void deveAtualizarPropriaAvaliacao() {
        Long eventoId = 10L;
        Long usuarioId = 1L;
        Usuario usuario = usuario(usuarioId);
        Evento evento = evento(eventoId, 2L);
        AvaliacaoEvento existente = AvaliacaoEvento.builder()
                .id(100L).usuario(usuario).evento(evento).nota(3).comentario("antigo").build();

        when(avaliacaoRepository.findByEventoIdAndUsuarioId(eventoId, usuarioId)).thenReturn(Optional.of(existente));
        when(avaliacaoRepository.save(any(AvaliacaoEvento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AvaliacaoEvento resultado = avaliacaoService.atualizar(eventoId, usuario, dto(4));

        assertEquals(Integer.valueOf(4), resultado.getNota());
        assertEquals("Comentario de teste.", resultado.getComentario());
        verify(avaliacaoRepository).save(existente);
    }

    @Test
    void deveExcluirPropriaAvaliacao() {
        Long eventoId = 10L;
        Long usuarioId = 1L;
        Usuario usuario = usuario(usuarioId);
        Evento evento = evento(eventoId, 2L);
        AvaliacaoEvento existente = AvaliacaoEvento.builder()
                .id(100L).usuario(usuario).evento(evento).nota(3).comentario("antigo").build();

        when(avaliacaoRepository.findByEventoIdAndUsuarioId(eventoId, usuarioId)).thenReturn(Optional.of(existente));

        avaliacaoService.excluir(eventoId, usuario);
        verify(avaliacaoRepository).delete(existente);
    }

    @Test
    void resumoDoEventoDeveCalcularMediaQuantidade() {
        Long eventoId = 10L;
        when(avaliacaoRepository.mediaNotasPorEvento(eventoId)).thenReturn(4.5);
        when(avaliacaoRepository.contarPorEvento(eventoId)).thenReturn(2L);

        AvaliacaoResumoDto resumo = avaliacaoService.resumoDoEvento(eventoId);

        assertTrue(resumo.isTemAvaliacoes());
        assertEquals(2L, resumo.getQuantidade());
        assertEquals(4.5, resumo.getMedia());
    }

    @Test
    void resumoDoEventoSemAvaliacoes() {
        Long eventoId = 10L;
        when(avaliacaoRepository.mediaNotasPorEvento(eventoId)).thenReturn(null);
        when(avaliacaoRepository.contarPorEvento(eventoId)).thenReturn(0L);

        AvaliacaoResumoDto resumo = avaliacaoService.resumoDoEvento(eventoId);

        assertFalse(resumo.isTemAvaliacoes());
        assertEquals(0L, resumo.getQuantidade());
        assertEquals(0.0, resumo.getMedia());
    }
}
