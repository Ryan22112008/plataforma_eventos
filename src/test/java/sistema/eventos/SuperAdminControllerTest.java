package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import sistema.eventos.controller.SuperAdminController;
import sistema.eventos.dto.HistoricoParticipacaoDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.StatusEvento;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.service.CertificadoService;
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.UsuarioService;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperAdminControllerTest {

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private InscricaoService inscricaoService;

    @Mock
    private CertificadoService certificadoService;

    @InjectMocks
    private SuperAdminController superAdminController;

    @Test
    void superAdminDeveBaixarCertificadoValidoDeOutroUsuario() throws Exception {
        Long usuarioId = 1L;
        Long inscricaoId = 100L;

        Usuario participante = new Usuario();
        participante.setId(usuarioId);
        participante.setNome("Participante Teste");
        participante.setEmail("participante@teste.com");

        Evento evento = new Evento();
        evento.setId(10L);
        evento.setTitulo("Curso de Java");
        evento.setDataInicio(LocalDate.of(2026, 10, 10));
        evento.setDataTermino(LocalDate.of(2026, 10, 12));
        evento.setStatus(StatusEvento.PUBLICADO);
        evento.setCargaHoraria(8);

        Inscricao inscricao = new Inscricao();
        inscricao.setId(inscricaoId);
        inscricao.setParticipante(participante);
        inscricao.setEvento(evento);
        inscricao.setStatus(StatusInscricao.UTILIZADO);

        byte[] pdfBytes = "PDF_MOCK".getBytes();

        when(inscricaoService.buscarPorId(inscricaoId)).thenReturn(inscricao);
        when(certificadoService.gerarCertificadoPdf(inscricao)).thenReturn(pdfBytes);

        MockHttpServletResponse response = new MockHttpServletResponse();
        superAdminController.baixarCertificadoParticipante(usuarioId, inscricaoId, response);

        assertArrayEquals(pdfBytes, response.getContentAsByteArray());
        assertEquals(MediaType.APPLICATION_PDF_VALUE, response.getContentType());
        assertEquals("attachment; filename=\"certificado-evento-100.pdf\"",
                response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
        verify(certificadoService).gerarCertificadoPdf(inscricao);
    }

    @Test
    void superAdminNaoDeveBaixarCertificadoDeInscricaoDeOutroUsuario() {
        Long usuarioIdUrl = 10L;
        Long inscricaoId = 100L;

        Usuario participanteOutro = new Usuario();
        participanteOutro.setId(20L);
        participanteOutro.setNome("Outro Usuario");
        participanteOutro.setEmail("outro@teste.com");

        Evento evento = new Evento();
        evento.setId(10L);
        evento.setTitulo("Evento");
        evento.setDataInicio(LocalDate.of(2026, 10, 1));
        evento.setDataTermino(LocalDate.of(2026, 10, 2));
        evento.setStatus(StatusEvento.PUBLICADO);

        Inscricao inscricao = new Inscricao();
        inscricao.setId(inscricaoId);
        inscricao.setParticipante(participanteOutro);
        inscricao.setEvento(evento);
        inscricao.setStatus(StatusInscricao.UTILIZADO);

        when(inscricaoService.buscarPorId(inscricaoId)).thenReturn(inscricao);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> superAdminController.baixarCertificadoParticipante(usuarioIdUrl, inscricaoId, new MockHttpServletResponse()));

        assertTrue(ex.getMessage().contains("não pertence ao usuário informado"));
        verifyNoInteractions(certificadoService);
    }

    @Test
    void inscricaoInexistenteDeveLancarErro() {
        Long usuarioId = 1L;
        Long inscricaoId = 999L;

        when(inscricaoService.buscarPorId(inscricaoId))
                .thenThrow(new IllegalArgumentException("Inscrição não encontrada com o ID: " + inscricaoId));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> superAdminController.baixarCertificadoParticipante(usuarioId, inscricaoId, new MockHttpServletResponse()));

        assertTrue(ex.getMessage().contains("Inscrição não encontrada"));
        verifyNoInteractions(certificadoService);
    }

    @Test
    void inscricaoNaoUtilizadaNaoDeveGerarCertificado() {
        Long usuarioId = 1L;
        Long inscricaoId = 100L;

        Usuario participante = new Usuario();
        participante.setId(usuarioId);
        participante.setNome("Participante");
        participante.setEmail("participante@teste.com");

        Evento evento = new Evento();
        evento.setId(10L);
        evento.setTitulo("Evento");
        evento.setDataInicio(LocalDate.of(2026, 10, 1));
        evento.setDataTermino(LocalDate.of(2026, 10, 2));
        evento.setStatus(StatusEvento.PUBLICADO);

        Inscricao inscricaoAtivo = new Inscricao();
        inscricaoAtivo.setId(inscricaoId);
        inscricaoAtivo.setParticipante(participante);
        inscricaoAtivo.setEvento(evento);
        inscricaoAtivo.setStatus(StatusInscricao.ATIVO);

        when(inscricaoService.buscarPorId(inscricaoId)).thenReturn(inscricaoAtivo);
        when(certificadoService.gerarCertificadoPdf(inscricaoAtivo))
                .thenThrow(new NegocioException("O certificado só pode ser emitido após a confirmação de presença."));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> superAdminController.baixarCertificadoParticipante(usuarioId, inscricaoId, new MockHttpServletResponse()));

        assertTrue(ex.getMessage().contains("O certificado só pode ser emitido após a confirmação de presença."));
        verify(certificadoService).gerarCertificadoPdf(inscricaoAtivo);
    }

    @Test
    void endpointDeveReutilizarCertificadoServiceGerarCertificadoPdf() {
        Long usuarioId = 1L;
        Long inscricaoId = 100L;

        Usuario participante = new Usuario();
        participante.setId(usuarioId);
        participante.setNome("Participante");
        participante.setEmail("participante@teste.com");

        Evento evento = new Evento();
        evento.setId(10L);
        evento.setTitulo("Evento");
        evento.setDataInicio(LocalDate.of(2026, 10, 1));
        evento.setDataTermino(LocalDate.of(2026, 10, 2));
        evento.setStatus(StatusEvento.PUBLICADO);

        Inscricao inscricao = new Inscricao();
        inscricao.setId(inscricaoId);
        inscricao.setParticipante(participante);
        inscricao.setEvento(evento);
        inscricao.setStatus(StatusInscricao.UTILIZADO);

        when(inscricaoService.buscarPorId(inscricaoId)).thenReturn(inscricao);
        when(certificadoService.gerarCertificadoPdf(inscricao)).thenReturn(new byte[0]);

        superAdminController.baixarCertificadoParticipante(usuarioId, inscricaoId, new MockHttpServletResponse());

        verify(certificadoService, times(1)).gerarCertificadoPdf(inscricao);
        verifyNoMoreInteractions(certificadoService);
    }

    @Test
    void historicoParticipacaoDeveConterInscricaoId() {
        HistoricoParticipacaoDto dto = HistoricoParticipacaoDto.builder()
                .inscricaoId(42L)
                .eventoId(10L)
                .tituloEvento("Evento Teste")
                .dataInicio(LocalDate.of(2026, 10, 1))
                .dataTermino(LocalDate.of(2026, 10, 2))
                .modalidade("PRESENCIAL")
                .statusParticipacao("Presente")
                .percentualFrequencia(100.0)
                .certificadoDisponivel(true)
                .build();

        assertEquals(42L, dto.getInscricaoId());
        assertEquals(10L, dto.getEventoId());
        assertTrue(dto.isCertificadoDisponivel());
    }
}