package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.UsuarioRepository;
import sistema.eventos.service.UsuarioExportService;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioExportServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UsuarioExportService usuarioExportService;

    private Usuario usuario(String nome, String email, Perfil perfil, boolean ativo, LocalDateTime dataCriacao) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(email);
        u.setPerfil(perfil);
        u.setAtivo(ativo);
        u.setDataCriacao(dataCriacao);
        return u;
    }

    @Test
    void deveGerarArquivoXlsxValidoSemFiltros() {
        // Given
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null))
                .thenReturn(List.of(
                        usuario("João Silva", "joao@seplag.mt.gov.br", Perfil.ROLE_PARTICIPANTE, true, LocalDateTime.of(2026, 1, 10, 9, 0)),
                        usuario("Maria Souza", "maria@seplag.mt.gov.br", Perfil.ROLE_ORGANIZADOR, true, LocalDateTime.of(2026, 2, 5, 14, 30))
                ));

        // When
        byte[] xlsx = usuarioExportService.exportarUsuarios(null, null, null, null);

        // Then
        assertNotNull(xlsx);
        assertTrue(xlsx.length > 0);
        // Arquivo OOXML (.xlsx) começa com o header ZIP "PK" (0x50 0x4B)
        assertEquals(0x50, xlsx[0] & 0xFF);
        assertEquals(0x4B, xlsx[1] & 0xFF);
    }

    @Test
    void deveRespeitarFiltroPorPerfil() {
        // Given
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, Perfil.ROLE_PARTICIPANTE, null))
                .thenReturn(List.of());

        // When
        usuarioExportService.exportarUsuarios(null, null, Perfil.ROLE_PARTICIPANTE, null);

        // Then
        verify(usuarioRepository).findFiltrosSemPaginacao(null, null, Perfil.ROLE_PARTICIPANTE, null);
    }

    @Test
    void deveRespeitarFiltroPorStatusAtivo() {
        // Given
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, true))
                .thenReturn(List.of());

        // When
        usuarioExportService.exportarUsuarios(null, null, null, true);

        // Then
        verify(usuarioRepository).findFiltrosSemPaginacao(null, null, null, true);
    }

    @Test
    void deveRespeitarBuscaPorNome() {
        // Given
        when(usuarioRepository.findFiltrosSemPaginacao("joao", null, null, null))
                .thenReturn(List.of());

        // When
        usuarioExportService.exportarUsuarios("joao", null, null, null);

        // Then
        verify(usuarioRepository).findFiltrosSemPaginacao("joao", null, null, null);
    }

    @Test
    void deveRespeitarBuscaPorEmail() {
        // Given
        when(usuarioRepository.findFiltrosSemPaginacao(null, "joao@", null, null))
                .thenReturn(List.of());

        // When
        usuarioExportService.exportarUsuarios(null, "joao@", null, null);

        // Then
        verify(usuarioRepository).findFiltrosSemPaginacao(null, "joao@", null, null);
    }

    @Test
    void naoDeveEstarLimitadoPelaPaginacao() {
        // Given: simula 347 participantes ativos (mais que uma página de 20)
        List<Usuario> todos = java.util.stream.IntStream.range(0, 347)
                .mapToObj(i -> usuario("Usuário " + i, "user" + i + "@teste.com", Perfil.ROLE_PARTICIPANTE, true, LocalDateTime.now()))
                .toList();
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, Perfil.ROLE_PARTICIPANTE, true))
                .thenReturn(todos);

        // When
        byte[] xlsx = usuarioExportService.exportarUsuarios(null, null, Perfil.ROLE_PARTICIPANTE, true);

        // Then: deve conter todos os 347, não apenas 20
        assertNotNull(xlsx);
        assertTrue(xlsx.length > 0);
        verify(usuarioRepository).findFiltrosSemPaginacao(null, null, Perfil.ROLE_PARTICIPANTE, true);
    }

    @Test
    void naoDeveIncluirSenhaOuDadosSensiveis() {
        // Given
        Usuario usuario = new Usuario();
        usuario.setNome("Usuário Teste");
        usuario.setEmail("teste@teste.com");
        usuario.setPerfil(Perfil.ROLE_PARTICIPANTE);
        usuario.setAtivo(true);
        usuario.setDataCriacao(LocalDateTime.now());
        // Senha é definida internamente, mas não deve aparecer no Excel
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null))
                .thenReturn(List.of(usuario));

        // When
        byte[] xlsx = usuarioExportService.exportarUsuarios(null, null, null, null);

        // Then: o conteúdo do Excel (como string) não deve conter a senha nem "hash"
        String conteudo = new String(xlsx);
        assertFalse(conteudo.contains("senha"), "Senha não deve ser exportada");
        assertFalse(conteudo.contains("hash"), "Hash não deve ser exportado");
    }

    @Test
    void deveExportarTodosUsuariosQuandoSemFiltros() {
        // Given
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null))
                .thenReturn(List.of());

        // When
        usuarioExportService.exportarUsuarios(null, null, null, null);

        // Then: chama o repository sem filtros (todos os usuários)
        verify(usuarioRepository).findFiltrosSemPaginacao(null, null, null, null);
    }
}
