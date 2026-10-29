package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import sistema.eventos.controller.EventoController;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.service.CertificadoService;
import sistema.eventos.service.EventoService;
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.UsuarioService;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventoControllerValidacaoLoteTest {

    @Mock private EventoService eventoService;
    @Mock private UsuarioService usuarioService;
    @Mock private InscricaoService inscricaoService;
    @Mock private SessaoRepository sessaoRepository;
    @Mock private CertificadoService certificadoService;
    @Mock private RedirectAttributes redirectAttributes;

    @InjectMocks private EventoController eventoController;

    @Test
    void organizadorSemAcessoAoEventoNaoPodeValidarLote() {
        Usuario dono = new Usuario();
        dono.setEmail("dono@seplag.mt.gov.br");
        Evento evento = new Evento();
        evento.setOrganizador(dono);
        when(eventoService.buscarPorId(10L)).thenReturn(evento);
        var autenticacao = new UsernamePasswordAuthenticationToken("outro@seplag.mt.gov.br", "senha",
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZADOR")));

        assertThatThrownBy(() -> eventoController.validarParticipacoesEmLote(
                10L, List.of(101L), autenticacao, redirectAttributes))
                .isInstanceOf(AccessDeniedException.class);

        verify(inscricaoService, never()).validarParticipacoesEmLote(10L, List.of(101L));
    }
}
