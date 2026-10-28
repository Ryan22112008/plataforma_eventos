package sistema.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.Usuario;
import sistema.eventos.service.FileStorageService;
import sistema.eventos.service.PerfilService;
import sistema.eventos.service.UsuarioService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PerfilServiceTest {

    private static final String EMAIL = "maria@seplag.mt.gov.br";

    @TempDir
    Path uploads;

    private UsuarioService usuarioService;
    private FileStorageService fileStorageService;
    private PerfilService perfilService;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        fileStorageService = new FileStorageService("", false, "", "", "", "");
        ReflectionTestUtils.setField(fileStorageService, "uploadDirectory", uploads.toString());
        perfilService = new PerfilService(usuarioService, fileStorageService);
        when(usuarioService.salvar(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void deveSalvarFotoValidaEVincularAoProprioUsuario() throws IOException {
        Usuario usuario = usuario(7L, EMAIL, null);
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(usuario);

        perfilService.atualizarFoto(EMAIL, imagemPng("minha-foto.png"));

        assertNotNull(usuario.getFotoPerfil());
        assertTrue(usuario.getFotoPerfil().matches("[A-Za-z0-9._-]+"));
        assertTrue(usuario.getFotoPerfil().contains("perfil-7"));
        assertTrue(Files.exists(uploads.resolve(usuario.getFotoPerfil())));
        assertNotNull(fileStorageService.publicImageUrl(usuario.getFotoPerfil()));
        verify(usuarioService).salvar(usuario);
    }

    @Test
    void deveRejeitarArquivoVazio() {
        Usuario usuario = usuario(7L, EMAIL, null);
        lenient().when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(usuario);

        MockMultipartFile vazio = new MockMultipartFile("foto", "vazia.png", "image/png", new byte[0]);

        NegocioException erro = assertThrows(NegocioException.class,
                () -> perfilService.atualizarFoto(EMAIL, vazio));

        assertTrue(erro.getMessage().contains("Selecione uma imagem"));
        verifyNoInteractions(usuarioService);
    }

    @Test
    void deveRejeitarTipoDeArquivoInvalido() {
        Usuario usuario = usuario(7L, EMAIL, null);
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(usuario);

        MockMultipartFile texto = new MockMultipartFile("foto", "arquivo.txt", "text/plain",
                "isto não é uma imagem".getBytes());

        NegocioException erro = assertThrows(NegocioException.class,
                () -> perfilService.atualizarFoto(EMAIL, texto));

        assertTrue(erro.getMessage().toLowerCase().contains("imagem"));
        assertNull(usuario.getFotoPerfil());
        verify(usuarioService, never()).salvar(any());
    }

    @Test
    void deveRejeitarArquivoAcimaDoLimite() {
        Usuario usuario = usuario(7L, EMAIL, null);
        lenient().when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(usuario);

        byte[] grande = new byte[(int) PerfilService.MAX_AVATAR_BYTES + 1];
        MockMultipartFile arquivo = new MockMultipartFile("foto", "grande.png", "image/png", grande);

        NegocioException erro = assertThrows(NegocioException.class,
                () -> perfilService.atualizarFoto(EMAIL, arquivo));

        assertTrue(erro.getMessage().contains("2 MB"));
        verifyNoInteractions(usuarioService);
    }

    @Test
    void naoDeveUsarNomeOriginalNemPermitirPathTraversal() {
        Usuario usuario = usuario(7L, EMAIL, null);
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(usuario);

        perfilService.atualizarFoto(EMAIL,
                imagemPng("../../fora-da-pasta.png"));

        String referencia = usuario.getFotoPerfil();
        assertNotNull(referencia);
        assertFalse(referencia.contains(".."));
        assertFalse(referencia.contains("/"));
        assertFalse(referencia.contains("\\"));
        assertFalse(referencia.contains("fora-da-pasta"));
        assertTrue(Files.exists(uploads.resolve(referencia)));
    }

    @Test
    void deveRemoverFotoEExibirAvatarPadrao() throws IOException {
        Path antiga = uploads.resolve("avatar-antigo.png");
        Files.write(antiga, pngValido());
        Usuario usuario = usuario(7L, EMAIL, "avatar-antigo.png");
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(usuario);

        perfilService.removerFoto(EMAIL);

        assertNull(usuario.getFotoPerfil());
        assertFalse(Files.exists(antiga));
        assertNull(fileStorageService.publicImageUrl(usuario.getFotoPerfil()));
        verify(usuarioService).salvar(usuario);
    }

    @Test
    void removerSemFotoNaoDeveFazerNada() {
        Usuario usuario = usuario(7L, EMAIL, null);
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(usuario);

        perfilService.removerFoto(EMAIL);

        assertNull(usuario.getFotoPerfil());
        verify(usuarioService, never()).salvar(any());
    }

    @Test
    void deveRemoverArquivoAntigoAoTrocarFoto() throws IOException {
        Path antiga = uploads.resolve("banner-antigo.png");
        Files.write(antiga, pngValido());
        Usuario usuario = usuario(7L, EMAIL, "banner-antigo.png");
        when(usuarioService.buscarPorEmail(EMAIL)).thenReturn(usuario);

        perfilService.atualizarFoto(EMAIL, imagemPng("nova.png"));

        assertFalse(Files.exists(antiga));
        assertTrue(Files.exists(uploads.resolve(usuario.getFotoPerfil())));
    }

    @Test
    void fotosDeUsuariosDiferentesNaoDevemSeConfundir() throws IOException {
        Usuario maria = usuario(1L, "maria@seplag.mt.gov.br", null);
        Usuario joao = usuario(2L, "joao@seplag.mt.gov.br", null);
        when(usuarioService.buscarPorEmail(maria.getEmail())).thenReturn(maria);
        when(usuarioService.buscarPorEmail(joao.getEmail())).thenReturn(joao);

        perfilService.atualizarFoto(maria.getEmail(), imagemPng("maria.png"));
        String referenciaMaria = maria.getFotoPerfil();

        perfilService.atualizarFoto(joao.getEmail(), imagemPng("joao.png"));
        String referenciaJoao = joao.getFotoPerfil();

        assertNotNull(referenciaMaria);
        assertNotNull(referenciaJoao);
        assertNotEquals(referenciaMaria, referenciaJoao);
        assertTrue(Files.exists(uploads.resolve(referenciaMaria)));
        assertTrue(Files.exists(uploads.resolve(referenciaJoao)));
        assertEquals(referenciaMaria, maria.getFotoPerfil());
    }

    private Usuario usuario(Long id, String email, String fotoPerfil) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome("Usuário " + id);
        usuario.setEmail(email);
        usuario.setSenha("senha-segura");
        usuario.setPerfil(Perfil.ROLE_PARTICIPANTE);
        usuario.setFotoPerfil(fotoPerfil);
        return usuario;
    }

    private MockMultipartFile imagemPng(String nomeOriginal) {
        try {
            return new MockMultipartFile("foto", nomeOriginal, "image/png", pngValido());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] pngValido() throws IOException {
        BufferedImage imagem = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            ImageIO.write(imagem, "png", saida);
            return saida.toByteArray();
        }
    }
}
