package sistema.eventos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import sistema.eventos.controller.EventoController;
import sistema.eventos.dto.InscritoFrequenciaDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.service.CertificadoService;
import sistema.eventos.service.EventoService;
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.UsuarioService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventoControllerFrequenciaTest {

    private static final Long EVENTO_ID = 10L;
    private static final Long INSCRICAO_ID = 101L;

    @Mock
    private EventoService eventoService;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private InscricaoService inscricaoService;

    @Mock
    private SessaoRepository sessaoRepository;

    @Mock
    private CertificadoService certificadoService;

    @InjectMocks
    private EventoController eventoController;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void organizadorDonoValidaParticipanteDoProprioEvento() {
        Evento evento = eventoDoOrganizador("dono@seplag.mt.gov.br");
        when(eventoService.buscarPorId(EVENTO_ID)).thenReturn(evento);
        when(inscricaoService.validarParticipacaoDoParticipante(EVENTO_ID, INSCRICAO_ID))
                .thenReturn(new Inscricao());

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();
        String view = eventoController.validarParticipacaoDoInscrito(
                EVENTO_ID, INSCRICAO_ID, autenticacao("dono@seplag.mt.gov.br", "ROLE_ORGANIZADOR"), attributes);

        assertThat(view).isEqualTo("redirect:/organizador/eventos/" + EVENTO_ID + "/inscritos");
        assertThat(attributes.getFlashAttributes()).containsKey("sucesso");
        verify(inscricaoService).validarParticipacaoDoParticipante(EVENTO_ID, INSCRICAO_ID);
    }

    @Test
    void organizadorDeOutroEventoNaoPodeValidarParticipante() {
        Evento evento = eventoDoOrganizador("dono@seplag.mt.gov.br");
        when(eventoService.buscarPorId(EVENTO_ID)).thenReturn(evento);

        assertThatThrownBy(() -> eventoController.validarParticipacaoDoInscrito(
                EVENTO_ID, INSCRICAO_ID, autenticacao("outro@seplag.mt.gov.br", "ROLE_ORGANIZADOR"),
                new RedirectAttributesModelMap()))
                .isInstanceOf(AccessDeniedException.class);

        verify(inscricaoService, never()).validarParticipacaoDoParticipante(EVENTO_ID, INSCRICAO_ID);
    }

    @Test
    void participanteNaoPodeValidarAPropriaParticipacao() {
        Evento evento = eventoDoOrganizador("dono@seplag.mt.gov.br");
        when(eventoService.buscarPorId(EVENTO_ID)).thenReturn(evento);

        assertThatThrownBy(() -> eventoController.validarParticipacaoDoInscrito(
                EVENTO_ID, INSCRICAO_ID, autenticacao("participante@seplag.mt.gov.br", "ROLE_PARTICIPANTE"),
                new RedirectAttributesModelMap()))
                .isInstanceOf(AccessDeniedException.class);

        verify(inscricaoService, never()).validarParticipacaoDoParticipante(EVENTO_ID, INSCRICAO_ID);
    }

    @Test
    void superAdminPodeValidarParticipanteMesmoSemSerOrganizador() {
        Evento evento = eventoDoOrganizador("dono@seplag.mt.gov.br");
        when(eventoService.buscarPorId(EVENTO_ID)).thenReturn(evento);
        when(inscricaoService.validarParticipacaoDoParticipante(EVENTO_ID, INSCRICAO_ID))
                .thenReturn(new Inscricao());

        String view = eventoController.validarParticipacaoDoInscrito(
                EVENTO_ID, INSCRICAO_ID, autenticacao("admin@seplag.mt.gov.br", "ROLE_SUPER_ADMIN"),
                new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/organizador/eventos/" + EVENTO_ID + "/inscritos");
        verify(inscricaoService).validarParticipacaoDoParticipante(EVENTO_ID, INSCRICAO_ID);
    }

    @Test
    void inscricaoInvalidaEhRejeitadaComMensagemDeErro() {
        Evento evento = eventoDoOrganizador("dono@seplag.mt.gov.br");
        when(eventoService.buscarPorId(EVENTO_ID)).thenReturn(evento);
        when(inscricaoService.validarParticipacaoDoParticipante(EVENTO_ID, INSCRICAO_ID))
                .thenThrow(new NegocioException("Inscrição não encontrada no sistema."));

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();
        String view = eventoController.validarParticipacaoDoInscrito(
                EVENTO_ID, INSCRICAO_ID, autenticacao("dono@seplag.mt.gov.br", "ROLE_ORGANIZADOR"), attributes);

        assertThat(view).isEqualTo("redirect:/organizador/eventos/" + EVENTO_ID + "/inscritos");
        assertThat(attributes.getFlashAttributes()).containsKey("erroNegocio");
    }

    @Test
    void listaInscritosUsaFrequenciaCalculadaNoBackend() {
        Evento evento = eventoDoOrganizador("dono@seplag.mt.gov.br");
        InscritoFrequenciaDto dto = InscritoFrequenciaDto.builder()
                .inscricaoId(INSCRICAO_ID)
                .nomeParticipante("Participante")
                .presencasPresentes(3)
                .totalSessoes(4)
                .percentualFrequencia(75.0)
                .build();

        when(eventoService.buscarPorId(EVENTO_ID)).thenReturn(evento);
        when(inscricaoService.listarInscritosComFrequencia(EVENTO_ID)).thenReturn(List.of(dto));
        SecurityContextHolder.getContext().setAuthentication(
                autenticacao("dono@seplag.mt.gov.br", "ROLE_ORGANIZADOR"));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = eventoController.listarInscritosDoEvento(
                EVENTO_ID, model, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("inscritos");
        assertThat(model.getAttribute("inscritos")).isEqualTo(List.of(dto));
        assertThat(model.getAttribute("paginaAtiva")).isEqualTo("inscritos");
    }

    private Evento eventoDoOrganizador(String email) {
        Usuario organizador = new Usuario();
        organizador.setId(1L);
        organizador.setEmail(email);

        Evento evento = new Evento();
        evento.setId(EVENTO_ID);
        evento.setTitulo("Evento de Teste");
        evento.setOrganizador(organizador);
        return evento;
    }

    private Authentication autenticacao(String email, String role) {
        return new UsernamePasswordAuthenticationToken(
                email, "senha", List.of(new SimpleGrantedAuthority(role)));
    }
}
