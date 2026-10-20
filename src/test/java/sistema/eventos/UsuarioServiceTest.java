package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.UsuarioRepository;
import sistema.eventos.service.UsuarioService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    void deveRemoverAcessoDeOrganizadorTransformandoEmParticipante() {
        // Given
        Long idOrganizador = 1L;
        Long idSuperAdmin = 2L;
        Usuario organizador = new Usuario();
        organizador.setId(idOrganizador);
        organizador.setNome("Organizador Teste");
        organizador.setEmail("organizador@teste.com");
        organizador.setPerfil(Perfil.ROLE_ORGANIZADOR);
        organizador.setAtivo(true);

        when(usuarioRepository.findById(idOrganizador)).thenReturn(Optional.of(organizador));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        usuarioService.removerAcessoOrganizador(idOrganizador, idSuperAdmin);

        // Then
        assertEquals(Perfil.ROLE_PARTICIPANTE, organizador.getPerfil());
        verify(usuarioRepository).save(organizador);
    }

    @Test
    void naoDeveDesativarPropriaConta() {
        // Given
        Long idProprio = 1L;

        // When & Then
        NegocioException ex = assertThrows(NegocioException.class,
                () -> usuarioService.desativarUsuario(idProprio, idProprio));
        assertEquals("Você não pode desativar a própria conta.", ex.getMessage());
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void naoDeveRemoverAcessoOrganizadorDaPropriaConta() {
        // Given
        Long idProprio = 1L;

        // When & Then
        NegocioException ex = assertThrows(NegocioException.class,
                () -> usuarioService.removerAcessoOrganizador(idProprio, idProprio));
        assertEquals("Você não pode alterar o próprio perfil.", ex.getMessage());
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void deveReativarUsuarioInativo() {
        // Given
        Long idUsuario = 1L;
        Usuario usuario = new Usuario();
        usuario.setId(idUsuario);
        usuario.setNome("Usuário Inativo");
        usuario.setEmail("inativo@teste.com");
        usuario.setPerfil(Perfil.ROLE_PARTICIPANTE);
        usuario.setAtivo(false);

        when(usuarioRepository.findById(idUsuario)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        usuarioService.reativarUsuario(idUsuario);

        // Then
        assertTrue(usuario.isAtivo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void naoDeveDesativarUsuarioJaInativo() {
        // Given
        Long idUsuario = 1L;
        Long idSuperAdmin = 2L;
        Usuario usuario = new Usuario();
        usuario.setId(idUsuario);
        usuario.setPerfil(Perfil.ROLE_PARTICIPANTE);
        usuario.setAtivo(false);

        when(usuarioRepository.findById(idUsuario)).thenReturn(Optional.of(usuario));

        // When & Then
        NegocioException ex = assertThrows(NegocioException.class,
                () -> usuarioService.desativarUsuario(idUsuario, idSuperAdmin));
        assertEquals("Este usuário já está inativo.", ex.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void naoDeveRemoverAcessoOrganizadorDeUsuarioNaoOrganizador() {
        // Given
        Long idParticipante = 1L;
        Long idSuperAdmin = 2L;
        Usuario participante = new Usuario();
        participante.setId(idParticipante);
        participante.setPerfil(Perfil.ROLE_PARTICIPANTE);

        when(usuarioRepository.findById(idParticipante)).thenReturn(Optional.of(participante));

        // When & Then
        NegocioException ex = assertThrows(NegocioException.class,
                () -> usuarioService.removerAcessoOrganizador(idParticipante, idSuperAdmin));
        assertEquals("Este usuário não possui perfil de organizador.", ex.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveDesativarUsuarioAtivo() {
        // Given
        Long idUsuario = 1L;
        Long idSuperAdmin = 2L;
        Usuario usuario = new Usuario();
        usuario.setId(idUsuario);
        usuario.setPerfil(Perfil.ROLE_PARTICIPANTE);
        usuario.setAtivo(true);

        when(usuarioRepository.findById(idUsuario)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        usuarioService.desativarUsuario(idUsuario, idSuperAdmin);

        // Then
        assertFalse(usuario.isAtivo());
        verify(usuarioRepository).save(usuario);
    }
}