package sistema.eventos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import sistema.eventos.controller.PerfilController;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.Usuario;
import sistema.eventos.service.FileStorageService;
import sistema.eventos.service.PerfilService;
import sistema.eventos.service.UsuarioService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PerfilController.class)
@AutoConfigureMockMvc(addFilters = false)
class PerfilControllerTest {

    private static final String EMAIL = "maria@seplag.mt.gov.br";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UsuarioService usuarioService;

    @MockBean
    private PerfilService perfilService;

    @MockBean
    private FileStorageService fileStorageService;

    @AfterEach
    void limparContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usuarioSemFotoDeveReceberAvatarPadrao() throws Exception {
        Authentication autenticacao = autenticar();
        Usuario usuario = usuario(1L, EMAIL, null);
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(usuario);
        when(fileStorageService.publicImageUrl(null)).thenReturn(null);

        mockMvc.perform(get("/perfil").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("Avatar padrão")))
                .andExpect(content().string(containsString("seplag-avatar--default")))
                .andExpect(content().string(containsString("Enviar foto")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Remover foto"))));
    }

    @Test
    void usuarioComFotoDeveVisualizarFotoAtualEBotaoDeRemocao() throws Exception {
        Authentication autenticacao = autenticar();
        Usuario usuario = usuario(1L, EMAIL, "foto-antiga.png");
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(usuario);
        when(fileStorageService.publicImageUrl("foto-antiga.png")).thenReturn("/imagens-eventos/foto-antiga.png");

        mockMvc.perform(get("/perfil").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/imagens-eventos/foto-antiga.png")))
                .andExpect(content().string(containsString("Sua foto atual")))
                .andExpect(content().string(containsString("Salvar nova foto")))
                .andExpect(content().string(containsString("Remover foto")));
    }

    @Test
    void usuarioAutenticadoDeveEnviarFotoParaOProprioEmail() throws Exception {
        Authentication autenticacao = autenticar();
        MockMultipartFile foto = new MockMultipartFile("foto", "avatar.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/perfil/foto")
                        .file(foto)
                        .param("usuarioId", "999")
                        .principal(autenticacao))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/perfil"))
                .andExpect(flash().attributeExists("sucessoPerfil"));

        verify(perfilService).atualizarFoto(eq(EMAIL), any());
        verify(perfilService, never()).atualizarFoto(eq("outro@seplag.mt.gov.br"), any());
        assertTrueContinuaAutenticado(autenticacao);
    }

    @Test
    void deveExibirErroQuandoEnvioForRejeitado() throws Exception {
        Authentication autenticacao = autenticar();
        MockMultipartFile foto = new MockMultipartFile("foto", "avatar.txt", "text/plain", new byte[]{1});
        doThrow(new NegocioException("Somente imagens PNG ou JPEG são aceitas."))
                .when(perfilService).atualizarFoto(eq(EMAIL), any());

        mockMvc.perform(multipart("/perfil/foto").file(foto).principal(autenticacao))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/perfil"))
                .andExpect(flash().attribute("erroPerfil", "Somente imagens PNG ou JPEG são aceitas."));

        assertTrueContinuaAutenticado(autenticacao);
    }

    @Test
    void remocaoDeveSerFeitaSomenteParaOUsuarioAutenticado() throws Exception {
        Authentication autenticacao = autenticar();

        mockMvc.perform(post("/perfil/foto/remover")
                        .param("usuarioId", "999")
                        .principal(autenticacao))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/perfil"))
                .andExpect(flash().attributeExists("sucessoPerfil"));

        verify(perfilService).removerFoto(EMAIL);
        assertTrueContinuaAutenticado(autenticacao);
    }

    private Authentication autenticar() {
        UserDetails userDetails = User.withUsername(EMAIL)
                .password("senha")
                .roles("PARTICIPANTE")
                .build();
        Authentication autenticacao = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(autenticacao);
        return autenticacao;
    }

    private void assertTrueContinuaAutenticado(Authentication esperada) {
        Authentication atual = SecurityContextHolder.getContext().getAuthentication();
        org.junit.jupiter.api.Assertions.assertNotNull(atual);
        org.junit.jupiter.api.Assertions.assertTrue(atual.isAuthenticated());
        org.junit.jupiter.api.Assertions.assertEquals(esperada.getName(), atual.getName());
    }

    private Usuario usuario(Long id, String email, String fotoPerfil) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome("Maria Silva");
        usuario.setEmail(email);
        usuario.setSenha("senha-segura");
        usuario.setPerfil(Perfil.ROLE_PARTICIPANTE);
        usuario.setFotoPerfil(fotoPerfil);
        return usuario;
    }
}
