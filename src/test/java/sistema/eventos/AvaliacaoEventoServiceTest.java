package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import sistema.eventos.dto.AvaliacaoEventoRequestDto;
import sistema.eventos.dto.AvaliacaoEventoResponseDto;
import sistema.eventos.dto.AvaliacaoResumoDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.AvaliacaoEvento;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.AvaliacaoEventoRepository;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.PresencaRepository;
import sistema.eventos.service.AvaliacaoEventoService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvaliacaoEventoServiceTest {

    private static final Long EVENTO_ID = 10L;
    private static final Long USUARIO_ID = 1L;
    private static final Long ORGANIZADOR_ID = 2L;

    @Mock
    private AvaliacaoEventoRepository avaliacaoEventoRepository;

    @Mock
    private EventoRepository eventoRepository;

    @Mock
    private PresencaRepository presencaRepository;

    @InjectMocks
    private AvaliacaoEventoService avaliacaoEventoService;

    @Test
    void deveCriarAvaliacaoQuandoUsuarioParticipou() {
        Usuario usuario = usuario(USUARIO_ID);
        Evento evento = evento(EVENTO_ID, ORGANIZADOR_ID);

        when(eventoRepository.findById(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(presencaRepository.countPresencasConfirmadasPorAluno(USUARIO_ID, EVENTO_ID)).thenReturn(1L);
        when(avaliacaoEventoRepository.existsByEventoIdAndUsuarioId(EVENTO_ID, USUARIO_ID)).thenReturn(false);
        when(avaliacaoEventoRepository.saveAndFlush(any(AvaliacaoEvento.class))).thenAnswer(invocation -> {
            AvaliacaoEvento avaliacao = invocation.getArgument(0);
            avaliacao.setId(99L);
            return avaliacao;
        });

        AvaliacaoEvento resultado = avaliacaoEventoService.criar(
                EVENTO_ID, usuario, dto(5, "  Excelente curso.  "));

        assertEquals(99L, resultado.getId());
        assertEquals(Integer.valueOf(5), resultado.getNota());
        assertEquals("Excelente curso.", resultado.getComentario());
        assertEquals(usuario, resultado.getUsuario());
        assertEquals(evento, resultado.getEvento());
        verify(avaliacaoEventoRepository).saveAndFlush(any(AvaliacaoEvento.class));
    }

    @Test
    void naoDeveCriarAvaliacaoSemPresencaConfirmada() {
        Usuario usuario = usuario(USUARIO_ID);
        Evento evento = evento(EVENTO_ID, ORGANIZADOR_ID);

        when(eventoRepository.findById(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(presencaRepository.countPresencasConfirmadasPorAluno(USUARIO_ID, EVENTO_ID)).thenReturn(0L);

        NegocioException ex = assertThrows(NegocioException.class,
                () -> avaliacaoEventoService.criar(EVENTO_ID, usuario, dto(5)));

        assertTrue(ex.getMessage().contains("participou"));
        verify(avaliacaoEventoRepository, never()).saveAndFlush(any(AvaliacaoEvento.class));
    }

    @Test
    void naoDeveCriarAvaliacaoQuandoUsuarioEhOrganizador() {
        Usuario organizador = usuario(USUARIO_ID);
        Evento evento = evento(EVENTO_ID, USUARIO_ID);
        evento.setOrganizador(organizador);

        when(eventoRepository.findById(EVENTO_ID)).thenReturn(Optional.of(evento));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> avaliacaoEventoService.criar(EVENTO_ID, organizador, dto(5)));

        assertTrue(ex.getMessage().contains("organizador"));
        verify(presencaRepository, never()).countPresencasConfirmadasPorAluno(anyLong(), anyLong());
        verify(avaliacaoEventoRepository, never()).saveAndFlush(any(AvaliacaoEvento.class));
    }

    @Test
    void naoDeveCriarAvaliacaoDuplicada() {
        Usuario usuario = usuario(USUARIO_ID);
        Evento evento = evento(EVENTO_ID, ORGANIZADOR_ID);

        when(eventoRepository.findById(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(presencaRepository.countPresencasConfirmadasPorAluno(USUARIO_ID, EVENTO_ID)).thenReturn(1L);
        when(avaliacaoEventoRepository.existsByEventoIdAndUsuarioId(EVENTO_ID, USUARIO_ID)).thenReturn(true);

        NegocioException ex = assertThrows(NegocioException.class,
                () -> avaliacaoEventoService.criar(EVENTO_ID, usuario, dto(5)));

        assertTrue(ex.getMessage().contains("já avaliou"));
        verify(avaliacaoEventoRepository, never()).saveAndFlush(any(AvaliacaoEvento.class));
    }

    @Test
    void naoDeveCriarAvaliacaoComNotaInvalida() {
        Usuario usuario = usuario(USUARIO_ID);
        Evento evento = evento(EVENTO_ID, ORGANIZADOR_ID);

        when(eventoRepository.findById(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(presencaRepository.countPresencasConfirmadasPorAluno(USUARIO_ID, EVENTO_ID)).thenReturn(1L);
        when(avaliacaoEventoRepository.existsByEventoIdAndUsuarioId(EVENTO_ID, USUARIO_ID)).thenReturn(false);

        for (Integer nota : List.of(0, 6, -1)) {
            NegocioException ex = assertThrows(NegocioException.class,
                    () -> avaliacaoEventoService.criar(EVENTO_ID, usuario, dto(nota)));
            assertTrue(ex.getMessage().contains("1 e 5"));
        }
        assertThrows(NegocioException.class,
                () -> avaliacaoEventoService.criar(EVENTO_ID, usuario, dto(null)));

        verify(avaliacaoEventoRepository, never()).saveAndFlush(any(AvaliacaoEvento.class));
    }

    @Test
    void naoDeveCriarAvaliacaoComComentarioVazio() {
        Usuario usuario = usuario(USUARIO_ID);
        Evento evento = evento(EVENTO_ID, ORGANIZADOR_ID);

        when(eventoRepository.findById(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(presencaRepository.countPresencasConfirmadasPorAluno(USUARIO_ID, EVENTO_ID)).thenReturn(1L);
        when(avaliacaoEventoRepository.existsByEventoIdAndUsuarioId(EVENTO_ID, USUARIO_ID)).thenReturn(false);

        NegocioException ex = assertThrows(NegocioException.class,
                () -> avaliacaoEventoService.criar(EVENTO_ID, usuario, dto(5, "   ")));

        assertTrue(ex.getMessage().contains("comentário"));
        verify(avaliacaoEventoRepository, never()).saveAndFlush(any(AvaliacaoEvento.class));
    }

    @Test
    void naoDeveCriarAvaliacaoComComentarioAcimaDoLimite() {
        Usuario usuario = usuario(USUARIO_ID);
        Evento evento = evento(EVENTO_ID, ORGANIZADOR_ID);

        when(eventoRepository.findById(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(presencaRepository.countPresencasConfirmadasPorAluno(USUARIO_ID, EVENTO_ID)).thenReturn(1L);
        when(avaliacaoEventoRepository.existsByEventoIdAndUsuarioId(EVENTO_ID, USUARIO_ID)).thenReturn(false);

        NegocioException ex = assertThrows(NegocioException.class,
                () -> avaliacaoEventoService.criar(EVENTO_ID, usuario, dto(5, "a".repeat(1001))));

        assertTrue(ex.getMessage().contains("1000"));
        verify(avaliacaoEventoRepository, never()).saveAndFlush(any(AvaliacaoEvento.class));
    }

    @Test
    void deveTraduzirViolacaoDeUnicidadeDoBancoAoSalvar() {
        Usuario usuario = usuario(USUARIO_ID);
        Evento evento = evento(EVENTO_ID, ORGANIZADOR_ID);

        when(eventoRepository.findById(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(presencaRepository.countPresencasConfirmadasPorAluno(USUARIO_ID, EVENTO_ID)).thenReturn(1L);
        when(avaliacaoEventoRepository.existsByEventoIdAndUsuarioId(EVENTO_ID, USUARIO_ID)).thenReturn(false);
        when(avaliacaoEventoRepository.saveAndFlush(any(AvaliacaoEvento.class)))
                .thenThrow(new DataIntegrityViolationException("uk_avaliacao_usuario_evento"));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> avaliacaoEventoService.criar(EVENTO_ID, usuario, dto(5)));

        assertTrue(ex.getMessage().contains("já avaliou"));
    }

    @Test
    void deveAtualizarPropriaAvaliacao() {
        Usuario usuario = usuario(USUARIO_ID);
        AvaliacaoEvento existente = avaliacao(100L, usuario, evento(EVENTO_ID, ORGANIZADOR_ID), 3, "Antigo");

        when(avaliacaoEventoRepository.findByEventoIdAndUsuarioId(EVENTO_ID, USUARIO_ID))
                .thenReturn(Optional.of(existente));
        when(avaliacaoEventoRepository.save(any(AvaliacaoEvento.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AvaliacaoEvento resultado = avaliacaoEventoService.atualizar(
                EVENTO_ID, usuario, dto(4, "  Comentário atualizado.  "));

        assertEquals(Integer.valueOf(4), resultado.getNota());
        assertEquals("Comentário atualizado.", resultado.getComentario());
        verify(avaliacaoEventoRepository).save(existente);
    }

    @Test
    void naoDeveAtualizarAvaliacaoDeOutroUsuario() {
        Usuario usuario = usuario(USUARIO_ID);

        when(avaliacaoEventoRepository.findByEventoIdAndUsuarioId(EVENTO_ID, USUARIO_ID))
                .thenReturn(Optional.empty());

        NegocioException ex = assertThrows(NegocioException.class,
                () -> avaliacaoEventoService.atualizar(EVENTO_ID, usuario, dto(4)));

        assertTrue(ex.getMessage().contains("não possui avaliação"));
        verify(avaliacaoEventoRepository, never()).save(any(AvaliacaoEvento.class));
    }

    @Test
    void deveExcluirPropriaAvaliacao() {
        Usuario usuario = usuario(USUARIO_ID);
        AvaliacaoEvento existente = avaliacao(100L, usuario, evento(EVENTO_ID, ORGANIZADOR_ID), 3, "Comentário");

        when(avaliacaoEventoRepository.findByEventoIdAndUsuarioId(EVENTO_ID, USUARIO_ID))
                .thenReturn(Optional.of(existente));

        avaliacaoEventoService.excluir(EVENTO_ID, usuario);

        verify(avaliacaoEventoRepository).delete(existente);
    }

    @Test
    void naoDeveExcluirAvaliacaoDeOutroUsuario() {
        Usuario usuario = usuario(USUARIO_ID);

        when(avaliacaoEventoRepository.findByEventoIdAndUsuarioId(EVENTO_ID, USUARIO_ID))
                .thenReturn(Optional.empty());

        NegocioException ex = assertThrows(NegocioException.class,
                () -> avaliacaoEventoService.excluir(EVENTO_ID, usuario));

        assertTrue(ex.getMessage().contains("não possui avaliação"));
        verify(avaliacaoEventoRepository, never()).delete(any(AvaliacaoEvento.class));
    }

    @Test
    void resumoDeveCalcularMediaEQuantidade() {
        when(avaliacaoEventoRepository.mediaNotasPorEvento(EVENTO_ID)).thenReturn(4.8);
        when(avaliacaoEventoRepository.contarPorEvento(EVENTO_ID)).thenReturn(127L);

        AvaliacaoResumoDto resumo = avaliacaoEventoService.obterResumo(EVENTO_ID);

        assertTrue(resumo.isTemAvaliacoes());
        assertEquals(4.8, resumo.getMedia(), 0.0001);
        assertEquals(127L, resumo.getQuantidade());
        assertEquals("4,8", resumo.getMediaFormatada());
        assertEquals(96.0, resumo.getPercentualEstrelas(), 0.0001);
    }

    @Test
    void resumoSemAvaliacoesNaoDeveExibirNota() {
        when(avaliacaoEventoRepository.mediaNotasPorEvento(EVENTO_ID)).thenReturn(null);
        when(avaliacaoEventoRepository.contarPorEvento(EVENTO_ID)).thenReturn(0L);

        AvaliacaoResumoDto resumo = avaliacaoEventoService.obterResumo(EVENTO_ID);

        assertFalse(resumo.isTemAvaliacoes());
        assertEquals(0L, resumo.getQuantidade());
        assertEquals(0.0, resumo.getMedia(), 0.0001);
        assertNull(resumo.getMediaFormatada());
    }

    @Test
    void deveListarAvaliacoesDoEventoComDadosDoParticipante() {
        Usuario maria = usuario(1L);
        maria.setNome("Maria Silva");
        Usuario joao = usuario(3L);
        joao.setNome("João Souza");
        LocalDateTime agora = LocalDateTime.of(2026, 10, 27, 10, 30);

        AvaliacaoEvento primeira = avaliacao(1L, maria, evento(EVENTO_ID, ORGANIZADOR_ID), 5, "Muito bom.");
        primeira.setDataCriacao(agora);
        AvaliacaoEvento segunda = avaliacao(2L, joao, evento(EVENTO_ID, ORGANIZADOR_ID), 4, "Bom.");
        segunda.setDataCriacao(agora.minusDays(1));

        Pageable pageable = PageRequest.of(0, 10);
        when(avaliacaoEventoRepository.findByEventoId(eq(EVENTO_ID), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(primeira, segunda), pageable, 2));

        Page<AvaliacaoEventoResponseDto> pagina = avaliacaoEventoService.listarDoEvento(EVENTO_ID, pageable);

        assertEquals(2, pagina.getTotalElements());
        assertEquals("Maria Silva", pagina.getContent().get(0).getNomeUsuario());
        assertEquals("João Souza", pagina.getContent().get(1).getNomeUsuario());
        assertEquals(Integer.valueOf(5), pagina.getContent().get(0).getNota());
    }

    @Test
    void devePermitirAvaliacaoDeParticipanteComPresenca() {
        Usuario usuario = usuario(USUARIO_ID);
        Evento evento = evento(EVENTO_ID, ORGANIZADOR_ID);

        when(presencaRepository.countPresencasConfirmadasPorAluno(USUARIO_ID, EVENTO_ID)).thenReturn(2L);

        assertTrue(avaliacaoEventoService.podeAvaliar(evento, usuario.getId()));
    }

    @Test
    void naoDevePermitirAvaliacaoDeParticipanteSemPresenca() {
        Usuario usuario = usuario(USUARIO_ID);
        Evento evento = evento(EVENTO_ID, ORGANIZADOR_ID);

        when(presencaRepository.countPresencasConfirmadasPorAluno(USUARIO_ID, EVENTO_ID)).thenReturn(0L);

        assertFalse(avaliacaoEventoService.podeAvaliar(evento, usuario.getId()));
    }

    @Test
    void naoDevePermitirAvaliacaoDoOrganizadorMesmoComPresenca() {
        Evento evento = evento(EVENTO_ID, USUARIO_ID);

        assertFalse(avaliacaoEventoService.podeAvaliar(evento, USUARIO_ID));
        verify(presencaRepository, never()).countPresencasConfirmadasPorAluno(anyLong(), anyLong());
    }

    private Usuario usuario(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome("Participante Teste");
        return usuario;
    }

    private Evento evento(Long id, Long organizadorId) {
        Evento evento = new Evento();
        evento.setId(id);
        evento.setTitulo("Evento Teste");
        evento.setOrganizador(usuario(organizadorId));
        return evento;
    }

    private AvaliacaoEvento avaliacao(Long id, Usuario usuario, Evento evento, Integer nota, String comentario) {
        return AvaliacaoEvento.builder()
                .id(id)
                .usuario(usuario)
                .evento(evento)
                .nota(nota)
                .comentario(comentario)
                .build();
    }

    private AvaliacaoEventoRequestDto dto(Integer nota) {
        return dto(nota, "Comentário de teste.");
    }

    private AvaliacaoEventoRequestDto dto(Integer nota, String comentario) {
        return AvaliacaoEventoRequestDto.builder()
                .nota(nota)
                .comentario(comentario)
                .build();
    }
}
