package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import sistema.eventos.config.SecurityConfig;
import sistema.eventos.controller.SuperAdminController;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.Usuario;
import sistema.eventos.service.CertificadoService;
import sistema.eventos.service.FileStorageService;
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.UsuarioExportService;
import sistema.eventos.service.UsuarioService;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SuperAdminController.class)
@Import(SecurityConfig.class)
class SuperAdminPromocaoSecurityTest {

    private static final String EMAIL_ADMIN = "admin@seplag.mt.gov.br";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UsuarioService usuarioService;

    @MockBean
    private UsuarioExportService usuarioExportService;

    @MockBean
    private InscricaoService inscricaoService;

    @MockBean
    private CertificadoService certificadoService;

    @MockBean
    private FileStorageService fileStorageService;

    @Test
    @WithMockUser(username = "participante@seplag.mt.gov.br", roles = "PARTICIPANTE")
    void participanteNaoPodePromoverUsuarioParaSuperAdmin() throws Exception {
        mockMvc.perform(post("/super-admin/usuarios/2/promover-super-admin").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(usuarioService);
    }

    @Test
    @WithMockUser(username = EMAIL_ADMIN, roles = "SUPER_ADMIN")
    void superAdminPodePromoverUsuarioExistente() throws Exception {
        Usuario admin = new Usuario();
        admin.setId(1L);
        admin.setEmail(EMAIL_ADMIN);
        admin.setPerfil(Perfil.ROLE_SUPER_ADMIN);
        when(usuarioService.buscarPorEmail(EMAIL_ADMIN)).thenReturn(admin);

        mockMvc.perform(post("/super-admin/usuarios/2/promover-super-admin").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/usuarios/2/detalhes"))
                .andExpect(flash().attributeExists("sucesso"));

        verify(usuarioService).promoverParaSuperAdmin(2L, 1L);
    }

    @Test
    @WithMockUser(username = EMAIL_ADMIN, roles = "SUPER_ADMIN")
    void detalheDeUsuarioComumDeveVoltarParaListaDeUsuarios() throws Exception {
        prepararAdminAutenticado();
        Usuario usuario = usuario(2L, "participante@seplag.mt.gov.br", Perfil.ROLE_PARTICIPANTE);
        when(usuarioService.buscarPorId(2L)).thenReturn(usuario);
        when(usuarioService.listarHistoricoParticipacao(2L)).thenReturn(List.of());
        when(usuarioService.listarEventosOrganizados(2L)).thenReturn(List.of());

        mockMvc.perform(get("/super-admin/usuarios/2/detalhes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("> Voltar")))
                .andExpect(content().string(containsString("href=\"/super-admin/usuarios\"")));
    }

    @Test
    @WithMockUser(username = EMAIL_ADMIN, roles = "SUPER_ADMIN")
    void detalheDeOrganizadorDeveVoltarParaListaDeOrganizadores() throws Exception {
        prepararAdminAutenticado();
        Usuario organizador = usuario(3L, "organizador@seplag.mt.gov.br", Perfil.ROLE_ORGANIZADOR);
        when(usuarioService.buscarPorId(3L)).thenReturn(organizador);
        when(usuarioService.listarHistoricoParticipacao(3L)).thenReturn(List.of());
        when(usuarioService.listarEventosOrganizados(3L)).thenReturn(List.of());

        mockMvc.perform(get("/super-admin/usuarios/3/detalhes"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("> Voltar")))
                .andExpect(content().string(containsString("href=\"/super-admin/organizadores\"")));
    }

    private void prepararAdminAutenticado() {
        when(usuarioService.buscarPorEmail(EMAIL_ADMIN)).thenReturn(usuario(
                1L, EMAIL_ADMIN, Perfil.ROLE_SUPER_ADMIN));
    }

    private static Usuario usuario(Long id, String email, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome("Usuário " + id);
        usuario.setEmail(email);
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        usuario.setDataCriacao(LocalDateTime.now());
        return usuario;
    }
}
