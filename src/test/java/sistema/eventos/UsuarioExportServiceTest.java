package sistema.eventos;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sistema.eventos.dto.UsuarioEventoTituloProjection;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.repository.UsuarioRepository;
import sistema.eventos.service.UsuarioExportService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioExportServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private InscricaoRepository inscricaoRepository;

    @Mock
    private EventoRepository eventoRepository;

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

    private Usuario usuarioComId(Long id, String nome, String email, Perfil perfil) {
        Usuario u = usuario(nome, email, perfil, true, LocalDateTime.of(2026, 1, 10, 9, 0));
        u.setId(id);
        return u;
    }

    private UsuarioEventoTituloProjection evento(Long usuarioId, String titulo) {
        return new EventoTituloProjection(usuarioId, titulo);
    }

    private List<List<String>> linhas(byte[] xlsx) throws IOException {
        List<List<String>> resultado = new ArrayList<>();
        DataFormatter formatador = new DataFormatter();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                List<String> celulas = new ArrayList<>();
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    Cell celula = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    celulas.add(celula == null ? "" : formatador.formatCellValue(celula));
                }
                resultado.add(celulas);
            }
        }
        return resultado;
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
        usuario.setSenha("$2a$10$hash-secreto-da-senha");
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null))
                .thenReturn(List.of(usuario));

        // When
        byte[] xlsx = usuarioExportService.exportarUsuarios(null, null, null, null);

        // Then: o conteúdo do Excel (como string) não deve conter a senha nem "hash"
        String conteudo = new String(xlsx);
        assertFalse(conteudo.contains("senha"), "Senha não deve ser exportada");
        assertFalse(conteudo.contains("hash"), "Hash não deve ser exportado");
        assertFalse(conteudo.contains("$2a$10$"), "Hash BCrypt não deve ser exportado");
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

    // ------------------------------------------------------------------
    // Eventos participados e organizados
    // ------------------------------------------------------------------

    @Test
    void usuarioSemEventosMostraNenhumNasDuasColunas() throws IOException {
        Usuario semEventos = usuarioComId(1L, "Sem Eventos", "sem@seplag.mt.gov.br", Perfil.ROLE_PARTICIPANTE);
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null)).thenReturn(List.of(semEventos));
        when(inscricaoRepository.findTitulosEventosPorParticipantes(List.of(1L), StatusInscricao.UTILIZADO))
                .thenReturn(List.of());
        when(eventoRepository.findTitulosEventosOrganizadosPorUsuarios(List.of(1L)))
                .thenReturn(List.of());

        List<List<String>> linhas = linhas(usuarioExportService.exportarUsuarios(null, null, null, null));

        assertEquals("Nenhum", linhas.get(1).get(5));
        assertEquals("Nenhum", linhas.get(1).get(6));
    }

    @Test
    void usuarioComUmEventoParticipadoExibeOTitulo() throws IOException {
        Usuario participante = usuarioComId(1L, "Ana", "ana@seplag.mt.gov.br", Perfil.ROLE_PARTICIPANTE);
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null)).thenReturn(List.of(participante));
        when(inscricaoRepository.findTitulosEventosPorParticipantes(List.of(1L), StatusInscricao.UTILIZADO))
                .thenReturn(List.of(evento(1L, "Workshop de Java")));
        when(eventoRepository.findTitulosEventosOrganizadosPorUsuarios(List.of(1L)))
                .thenReturn(List.of());

        List<List<String>> linhas = linhas(usuarioExportService.exportarUsuarios(null, null, null, null));

        assertEquals("Workshop de Java", linhas.get(1).get(5));
        assertEquals("Nenhum", linhas.get(1).get(6));
    }

    @Test
    void usuarioComVariosEventosParticipadosExibeUmPorLinha() throws IOException {
        Usuario participante = usuarioComId(1L, "Ana", "ana@seplag.mt.gov.br", Perfil.ROLE_PARTICIPANTE);
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null)).thenReturn(List.of(participante));
        when(inscricaoRepository.findTitulosEventosPorParticipantes(List.of(1L), StatusInscricao.UTILIZADO))
                .thenReturn(List.of(
                        evento(1L, "Workshop de Java"),
                        evento(1L, "Congresso de Tecnologia"),
                        evento(1L, "Curso de Spring Boot")));
        when(eventoRepository.findTitulosEventosOrganizadosPorUsuarios(List.of(1L)))
                .thenReturn(List.of());

        List<List<String>> linhas = linhas(usuarioExportService.exportarUsuarios(null, null, null, null));

        assertEquals("Workshop de Java\nCongresso de Tecnologia\nCurso de Spring Boot", linhas.get(1).get(5));
        assertEquals(2, linhas.size(), "Usuário deve continuar em uma única linha (cabeçalho + 1)");
    }

    @Test
    void participacaoConsideraSomenteInscricaoUtilizada() throws IOException {
        Usuario participante = usuarioComId(1L, "Ana", "ana@seplag.mt.gov.br", Perfil.ROLE_PARTICIPANTE);
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null)).thenReturn(List.of(participante));
        when(inscricaoRepository.findTitulosEventosPorParticipantes(List.of(1L), StatusInscricao.UTILIZADO))
                .thenReturn(List.of(evento(1L, "Evento Validado")));
        when(eventoRepository.findTitulosEventosOrganizadosPorUsuarios(List.of(1L)))
                .thenReturn(List.of());

        List<List<String>> linhas = linhas(usuarioExportService.exportarUsuarios(null, null, null, null));

        assertEquals("Evento Validado", linhas.get(1).get(5));
        // A consulta é feita com o status de participação validada, nunca com inscrição genérica
        verify(inscricaoRepository)
                .findTitulosEventosPorParticipantes(List.of(1L), StatusInscricao.UTILIZADO);
    }

    @Test
    void usuarioOrganizadorComUmEventoExibeOTitulo() throws IOException {
        Usuario organizador = usuarioComId(2L, "Bruno", "bruno@seplag.mt.gov.br", Perfil.ROLE_ORGANIZADOR);
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null)).thenReturn(List.of(organizador));
        when(inscricaoRepository.findTitulosEventosPorParticipantes(List.of(2L), StatusInscricao.UTILIZADO))
                .thenReturn(List.of());
        when(eventoRepository.findTitulosEventosOrganizadosPorUsuarios(List.of(2L)))
                .thenReturn(List.of(evento(2L, "Semana de Tecnologia 2026")));

        List<List<String>> linhas = linhas(usuarioExportService.exportarUsuarios(null, null, null, null));

        assertEquals("Nenhum", linhas.get(1).get(5));
        assertEquals("Semana de Tecnologia 2026", linhas.get(1).get(6));
    }

    @Test
    void usuarioOrganizadorComVariosEventosExibeUmPorLinha() throws IOException {
        Usuario organizador = usuarioComId(2L, "Bruno", "bruno@seplag.mt.gov.br", Perfil.ROLE_ORGANIZADOR);
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null)).thenReturn(List.of(organizador));
        when(inscricaoRepository.findTitulosEventosPorParticipantes(List.of(2L), StatusInscricao.UTILIZADO))
                .thenReturn(List.of());
        when(eventoRepository.findTitulosEventosOrganizadosPorUsuarios(List.of(2L)))
                .thenReturn(List.of(
                        evento(2L, "Semana de Tecnologia 2026"),
                        evento(2L, "Workshop SEPLAG")));

        List<List<String>> linhas = linhas(usuarioExportService.exportarUsuarios(null, null, null, null));

        assertEquals("Semana de Tecnologia 2026\nWorkshop SEPLAG", linhas.get(1).get(6));
        assertEquals(2, linhas.size());
    }

    @Test
    void usuarioQueParticipaEOrganizaPreencheAsDuasColunas() throws IOException {
        Usuario ambos = usuarioComId(3L, "Carla", "carla@seplag.mt.gov.br", Perfil.ROLE_ORGANIZADOR);
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null)).thenReturn(List.of(ambos));
        when(inscricaoRepository.findTitulosEventosPorParticipantes(List.of(3L), StatusInscricao.UTILIZADO))
                .thenReturn(List.of(evento(3L, "Curso de Gestão")));
        when(eventoRepository.findTitulosEventosOrganizadosPorUsuarios(List.of(3L)))
                .thenReturn(List.of(evento(3L, "Encontro de Servidores")));

        List<List<String>> linhas = linhas(usuarioExportService.exportarUsuarios(null, null, null, null));

        assertEquals("Curso de Gestão", linhas.get(1).get(5));
        assertEquals("Encontro de Servidores", linhas.get(1).get(6));
    }

    @Test
    void doisUsuariosNaoMisturamOsEventos() throws IOException {
        Usuario ana = usuarioComId(1L, "Ana", "ana@seplag.mt.gov.br", Perfil.ROLE_PARTICIPANTE);
        Usuario bruno = usuarioComId(2L, "Bruno", "bruno@seplag.mt.gov.br", Perfil.ROLE_ORGANIZADOR);
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null)).thenReturn(List.of(ana, bruno));
        when(inscricaoRepository.findTitulosEventosPorParticipantes(List.of(1L, 2L), StatusInscricao.UTILIZADO))
                .thenReturn(List.of(
                        evento(1L, "Evento da Ana"),
                        evento(2L, "Evento do Bruno")));
        when(eventoRepository.findTitulosEventosOrganizadosPorUsuarios(List.of(1L, 2L)))
                .thenReturn(List.of(
                        evento(1L, "Organizado pela Ana"),
                        evento(2L, "Organizado pelo Bruno")));

        List<List<String>> linhas = linhas(usuarioExportService.exportarUsuarios(null, null, null, null));

        assertEquals("Evento da Ana", linhas.get(1).get(5));
        assertEquals("Organizado pela Ana", linhas.get(1).get(6));
        assertEquals("Evento do Bruno", linhas.get(2).get(5));
        assertEquals("Organizado pelo Bruno", linhas.get(2).get(6));
    }

    @Test
    void carregaEventosEmLoteSemConsultasPorUsuario() {
        Usuario ana = usuarioComId(1L, "Ana", "ana@seplag.mt.gov.br", Perfil.ROLE_PARTICIPANTE);
        Usuario bruno = usuarioComId(2L, "Bruno", "bruno@seplag.mt.gov.br", Perfil.ROLE_PARTICIPANTE);
        Usuario carla = usuarioComId(3L, "Carla", "carla@seplag.mt.gov.br", Perfil.ROLE_PARTICIPANTE);
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null))
                .thenReturn(List.of(ana, bruno, carla));
        when(inscricaoRepository.findTitulosEventosPorParticipantes(anyList(), eq(StatusInscricao.UTILIZADO)))
                .thenReturn(List.of());
        when(eventoRepository.findTitulosEventosOrganizadosPorUsuarios(anyList()))
                .thenReturn(List.of());

        usuarioExportService.exportarUsuarios(null, null, null, null);

        // Duas consultas no total, independentemente da quantidade de usuários.
        verify(inscricaoRepository, times(1))
                .findTitulosEventosPorParticipantes(anyList(), eq(StatusInscricao.UTILIZADO));
        verify(eventoRepository, times(1)).findTitulosEventosOrganizadosPorUsuarios(anyList());
        verify(usuarioRepository, times(1)).findFiltrosSemPaginacao(null, null, null, null);
    }

    @Test
    void cadaUsuarioOcupaUmaUnicaLinha() throws IOException {
        Usuario ana = usuarioComId(1L, "Ana", "ana@seplag.mt.gov.br", Perfil.ROLE_PARTICIPANTE);
        Usuario bruno = usuarioComId(2L, "Bruno", "bruno@seplag.mt.gov.br", Perfil.ROLE_ORGANIZADOR);
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null)).thenReturn(List.of(ana, bruno));
        when(inscricaoRepository.findTitulosEventosPorParticipantes(List.of(1L, 2L), StatusInscricao.UTILIZADO))
                .thenReturn(List.of(evento(1L, "Java"), evento(1L, "Spring"), evento(2L, "Cloud")));
        when(eventoRepository.findTitulosEventosOrganizadosPorUsuarios(List.of(1L, 2L)))
                .thenReturn(List.of());

        List<List<String>> linhas = linhas(usuarioExportService.exportarUsuarios(null, null, null, null));

        assertEquals(3, linhas.size(), "Cabeçalho + 2 usuários, sem uma linha por evento");
        assertEquals(1, linhas.stream().filter(l -> l.get(0).equals("Ana")).count());
        assertEquals(1, linhas.stream().filter(l -> l.get(0).equals("Bruno")).count());
        assertEquals("Java\nSpring", linhas.get(1).get(5));
        assertEquals("Cloud", linhas.get(2).get(5));
    }

    @Test
    void filtrosContinuamSendoRespeitadosComAsNovasColunas() throws IOException {
        Usuario joao = usuarioComId(1L, "João", "joao@seplag.mt.gov.br", Perfil.ROLE_ORGANIZADOR);
        when(usuarioRepository.findFiltrosSemPaginacao("joao", null, Perfil.ROLE_ORGANIZADOR, true))
                .thenReturn(List.of(joao));
        when(inscricaoRepository.findTitulosEventosPorParticipantes(List.of(1L), StatusInscricao.UTILIZADO))
                .thenReturn(List.of(evento(1L, "Curso de Java")));
        when(eventoRepository.findTitulosEventosOrganizadosPorUsuarios(List.of(1L)))
                .thenReturn(List.of(evento(1L, "Semana de Tecnologia")));

        List<List<String>> linhas = linhas(
                usuarioExportService.exportarUsuarios("joao", null, Perfil.ROLE_ORGANIZADOR, true));

        assertEquals(2, linhas.size(), "Apenas o usuário filtrado deve ser exportado");
        assertEquals("João", linhas.get(1).get(0));
        assertEquals("Curso de Java", linhas.get(1).get(5));
        assertEquals("Semana de Tecnologia", linhas.get(1).get(6));
        verify(usuarioRepository).findFiltrosSemPaginacao("joao", null, Perfil.ROLE_ORGANIZADOR, true);
    }

    @Test
    void cabecalhoPreservaColunasExistentesEAdicionaAsDuasNovas() throws IOException {
        when(usuarioRepository.findFiltrosSemPaginacao(null, null, null, null)).thenReturn(List.of());

        List<List<String>> linhas = linhas(usuarioExportService.exportarUsuarios(null, null, null, null));

        assertEquals(List.of("Nome", "E-mail", "Perfil", "Status", "Data de cadastro",
                "Eventos Participados", "Eventos Organizados"), linhas.get(0));
    }

    private record EventoTituloProjection(Long usuarioId, String titulo) implements UsuarioEventoTituloProjection {

        @Override
        public Long getUsuarioId() {
            return usuarioId;
        }

        @Override
        public String getTitulo() {
            return titulo;
        }
    }
}
