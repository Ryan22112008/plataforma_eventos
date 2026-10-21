package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.ui.Model;

import sistema.eventos.controller.DashboardController;
import sistema.eventos.model.StatusSolicitacao;
import sistema.eventos.repository.SolicitacaoRepository;
import sistema.eventos.service.EventoService;
import sistema.eventos.service.UsuarioService;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private EventoService eventoService;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private SolicitacaoRepository solicitacaoRepository;

    @Mock
    private Model model;

    @InjectMocks
    private DashboardController dashboardController;

    @Test
    void superAdminDeveSerRedirecionadoParaSuperAdminUsuariosENaoParaLogin() {
        // Given: usuário autenticado com ROLE_SUPER_ADMIN
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getAuthorities()).thenReturn((java.util.Collection) AuthorityUtils.createAuthorityList("ROLE_SUPER_ADMIN"));

        // When
        String view = dashboardController.abrirDashboard(auth, null);

        // Then: deve ir para /super-admin/usuarios, nunca de volta para /login
        assertEquals("redirect:/super-admin/usuarios", view);
    }

    @Test
    void adminDeveSerRedirecionadoParaAdminSolicitacoes() {
        // Given: usuário autenticado com ROLE_ADMIN
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getAuthorities()).thenReturn((java.util.Collection) AuthorityUtils.createAuthorityList("ROLE_ADMIN"));

        // When
        String view = dashboardController.abrirDashboard(auth, null);

        // Then
        assertEquals("redirect:/admin/solicitacoes", view);
    }

    @Test
    void organizadorDeveRetornarPainelOrganizador() {
        // Given: usuário autenticado com ROLE_ORGANIZADOR
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getAuthorities()).thenReturn((java.util.Collection) AuthorityUtils.createAuthorityList("ROLE_ORGANIZADOR"));
        when(auth.getName()).thenReturn("organizador@teste.com");

        sistema.eventos.model.Usuario organizador = new sistema.eventos.model.Usuario();
        organizador.setId(1L);
        organizador.setEmail("organizador@teste.com");
        organizador.setPerfil(sistema.eventos.model.Perfil.ROLE_ORGANIZADOR);

        when(usuarioService.buscarPorEmail("organizador@teste.com")).thenReturn(organizador);
        when(eventoService.listarPorOrganizador(1L)).thenReturn(java.util.List.of());

        // When
        String view = dashboardController.abrirDashboard(auth, model);

        // Then
        assertEquals("painel-organizador", view);
    }

    @Test
    void participanteDeveSerRedirecionadoParaEventos() {
        // Given: usuário autenticado com ROLE_PARTICIPANTE
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getAuthorities()).thenReturn((java.util.Collection) AuthorityUtils.createAuthorityList("ROLE_PARTICIPANTE"));
        when(auth.getName()).thenReturn("participante@teste.com");
        when(solicitacaoRepository.existsByUsuario_EmailIgnoreCaseAndStatus(
                "participante@teste.com", StatusSolicitacao.PENDENTE)).thenReturn(false);

        // When
        String view = dashboardController.abrirDashboard(auth, null);

        // Then
        assertEquals("redirect:/eventos", view);
    }

    @Test
    void usuarioNaoAutenticadoDeveSerRedirecionadoParaLogin() {
        // Given: usuário não autenticado
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);

        // When
        String view = dashboardController.abrirDashboard(auth, null);

        // Then
        assertEquals("redirect:/login", view);
    }

    @Test
    void authenticationNuloDeveSerRedirecionadoParaLogin() {
        // When
        String view = dashboardController.abrirDashboard(null, null);

        // Then
        assertEquals("redirect:/login", view);
    }
}
