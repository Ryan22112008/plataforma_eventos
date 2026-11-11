package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sistema.eventos.dto.HistoricoEventosOrganizadosDto;
import sistema.eventos.dto.HistoricoParticipacaoDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.ModalidadeEvento;
import sistema.eventos.model.NichoEvento;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.StatusEvento;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.repository.UsuarioRepository;
import sistema.eventos.service.PresencaService;
import sistema.eventos.service.UsuarioService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private InscricaoRepository inscricaoRepository;

    @Mock
    private PresencaService presencaService;

    @Mock
    private EventoRepository eventoRepository;

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

    // --- Testes de Promoção para Super Admin ---

    @Test
    void devePromoverParticipanteParaSuperAdminPreservandoDadosDaConta() {
        Usuario superAdmin = usuario(1L, "admin@seplag.mt.gov.br", "Admin", Perfil.ROLE_SUPER_ADMIN);
        Usuario participante = usuario(2L, "maria@seplag.mt.gov.br", "Maria", Perfil.ROLE_PARTICIPANTE);
        participante.setFotoPerfil("foto-maria.png");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(superAdmin));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(participante));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        usuarioService.promoverParaSuperAdmin(2L, 1L);

        assertEquals(Perfil.ROLE_SUPER_ADMIN, participante.getPerfil());
        assertEquals("maria@seplag.mt.gov.br", participante.getEmail());
        assertEquals("Maria", participante.getNome());
        assertEquals("foto-maria.png", participante.getFotoPerfil());
        verify(usuarioRepository).save(participante);
    }

    @Test
    void devePromoverOrganizadorEAvaliadorParaSuperAdmin() {
        Usuario superAdmin = usuario(1L, "admin@seplag.mt.gov.br", "Admin", Perfil.ROLE_SUPER_ADMIN);
        Usuario organizador = usuario(2L, "org@seplag.mt.gov.br", "Org", Perfil.ROLE_ORGANIZADOR);
        Usuario avaliador = usuario(3L, "aval@seplag.mt.gov.br", "Aval", Perfil.ROLE_AVALIADOR);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(superAdmin));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(organizador));
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(avaliador));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        usuarioService.promoverParaSuperAdmin(2L, 1L);
        usuarioService.promoverParaSuperAdmin(3L, 1L);

        assertEquals(Perfil.ROLE_SUPER_ADMIN, organizador.getPerfil());
        assertEquals(Perfil.ROLE_SUPER_ADMIN, avaliador.getPerfil());
    }

    @Test
    void naoDevePromoverUsuarioQueJaESuperAdmin() {
        Usuario superAdmin = usuario(1L, "admin@seplag.mt.gov.br", "Admin", Perfil.ROLE_SUPER_ADMIN);
        Usuario outroSuperAdmin = usuario(2L, "admin2@seplag.mt.gov.br", "Admin 2", Perfil.ROLE_SUPER_ADMIN);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(superAdmin));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(outroSuperAdmin));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> usuarioService.promoverParaSuperAdmin(2L, 1L));

        assertEquals("Este usuário já é SUPER_ADMIN.", ex.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void participanteNaoPodePromoverUsuario() {
        Usuario participante = usuario(2L, "participante@seplag.mt.gov.br", "Participante", Perfil.ROLE_PARTICIPANTE);

        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(participante));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> usuarioService.promoverParaSuperAdmin(3L, 2L));

        assertEquals("Apenas um Super Admin pode promover usuários.", ex.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void superAdminNaoPodePromoverAPropriaContaNovamente() {
        Usuario superAdmin = usuario(1L, "admin@seplag.mt.gov.br", "Admin", Perfil.ROLE_SUPER_ADMIN);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(superAdmin));

        NegocioException ex = assertThrows(NegocioException.class,
                () -> usuarioService.promoverParaSuperAdmin(1L, 1L));

        assertEquals("Este usuário já é SUPER_ADMIN.", ex.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    private static Usuario usuario(Long id, String email, String nome, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setEmail(email);
        usuario.setNome(nome);
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        return usuario;
    }

    // --- Testes de Histórico de Participação ---

    @Test
    void usuarioSemParticipacoesRetornaListaVazia() {
        Long usuarioId = 1L;
        when(inscricaoRepository.findByParticipanteIdComEvento(usuarioId)).thenReturn(List.of());
        List<HistoricoParticipacaoDto> resultado = usuarioService.listarHistoricoParticipacao(usuarioId);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void inscricaoAtivoNaoApareceComoParticipacaoConcluida() {
        Long usuarioId = 1L;
        Evento evento = new Evento();
        evento.setId(10L);
        evento.setTitulo("Evento Teste");
        evento.setDataInicio(LocalDate.of(2026, 10, 10));
        Inscricao inscricaoAtivo = new Inscricao();
        inscricaoAtivo.setId(100L);
        inscricaoAtivo.setEvento(evento);
        inscricaoAtivo.setStatus(StatusInscricao.ATIVO);
        when(inscricaoRepository.findByParticipanteIdComEvento(usuarioId)).thenReturn(List.of(inscricaoAtivo));
        List<HistoricoParticipacaoDto> resultado = usuarioService.listarHistoricoParticipacao(usuarioId);
        assertTrue(resultado.isEmpty(), "Inscrição ATIVO não deve aparecer como participação concluída");
    }

    @Test
    void inscricaoCanceladoNaoApareceNoHistorico() {
        Long usuarioId = 1L;
        Evento evento = new Evento();
        evento.setId(10L);
        evento.setTitulo("Evento Teste");
        evento.setDataInicio(LocalDate.of(2026, 10, 10));
        Inscricao inscricaoCancelado = new Inscricao();
        inscricaoCancelado.setId(100L);
        inscricaoCancelado.setEvento(evento);
        inscricaoCancelado.setStatus(StatusInscricao.CANCELADO);
        when(inscricaoRepository.findByParticipanteIdComEvento(usuarioId)).thenReturn(List.of(inscricaoCancelado));
        List<HistoricoParticipacaoDto> resultado = usuarioService.listarHistoricoParticipacao(usuarioId);
        assertTrue(resultado.isEmpty(), "Inscrição CANCELADO não deve aparecer no histórico");
    }

    @Test
    void inscricaoUtilizadoApareceNoHistorico() {
        Long usuarioId = 1L;
        Evento evento = new Evento();
        evento.setId(10L);
        evento.setTitulo("Curso de Java");
        evento.setDataInicio(LocalDate.of(2026, 10, 10));
        evento.setDataTermino(LocalDate.of(2026, 10, 12));
        evento.setCertificadoTemplate("frente.png");
        evento.setCertificadoTemplateVerso("verso.png");
        Inscricao inscricaoUtilizado = new Inscricao();
        inscricaoUtilizado.setId(100L);
        inscricaoUtilizado.setEvento(evento);
        inscricaoUtilizado.setStatus(StatusInscricao.UTILIZADO);
        when(inscricaoRepository.findByParticipanteIdComEvento(usuarioId)).thenReturn(List.of(inscricaoUtilizado));
        when(presencaService.calcularFrequencia(usuarioId, 10L)).thenReturn(100.0);
        List<HistoricoParticipacaoDto> resultado = usuarioService.listarHistoricoParticipacao(usuarioId);
        assertEquals(1, resultado.size());
        assertEquals("Curso de Java", resultado.get(0).getTituloEvento());
        assertEquals(10L, resultado.get(0).getEventoId());
        assertEquals("Presente", resultado.get(0).getStatusParticipacao());
        assertTrue(resultado.get(0).isCertificadoDisponivel());
    }

    @Test
    void eventoCorretoAssociadoAoParticipanteCorreto() {
        Long usuarioId = 1L;
        Evento eventoA = new Evento();
        eventoA.setId(1L);
        eventoA.setTitulo("Evento A");
        eventoA.setDataInicio(LocalDate.of(2026, 1, 1));
        eventoA.setCertificadoTemplate("f.png");
        eventoA.setCertificadoTemplateVerso("v.png");
        Evento eventoB = new Evento();
        eventoB.setId(2L);
        eventoB.setTitulo("Evento B");
        eventoB.setDataInicio(LocalDate.of(2026, 2, 1));
        eventoB.setCertificadoTemplate("f.png");
        eventoB.setCertificadoTemplateVerso("v.png");
        Inscricao inscricaoA = new Inscricao();
        inscricaoA.setEvento(eventoA);
        inscricaoA.setStatus(StatusInscricao.UTILIZADO);
        Inscricao inscricaoB = new Inscricao();
        inscricaoB.setEvento(eventoB);
        inscricaoB.setStatus(StatusInscricao.UTILIZADO);
        when(inscricaoRepository.findByParticipanteIdComEvento(usuarioId))
                .thenReturn(List.of(inscricaoA, inscricaoB));
        when(presencaService.calcularFrequencia(anyLong(), anyLong())).thenReturn(100.0);
        List<HistoricoParticipacaoDto> resultado = usuarioService.listarHistoricoParticipacao(usuarioId);
        assertEquals(2, resultado.size());
        assertEquals("Evento B", resultado.get(0).getTituloEvento());
        assertEquals("Evento A", resultado.get(1).getTituloEvento());
    }

    @Test
    void usuarioComMultiplosEventosRecebeTodosSemLimiteDePaginacao() {
        Long usuarioId = 1L;
        List<Inscricao> inscricoes = java.util.stream.IntStream.range(0, 25)
                .mapToObj(i -> {
                    Evento e = new Evento();
                    e.setId((long) i);
                    e.setTitulo("Evento " + i);
                    e.setDataInicio(LocalDate.of(2026, 10, 1));
                    e.setCertificadoTemplate("f.png");
                    e.setCertificadoTemplateVerso("v.png");
                    Inscricao insc = new Inscricao();
                    insc.setEvento(e);
                    insc.setStatus(StatusInscricao.UTILIZADO);
                    return insc;
                })
                .toList();
        when(inscricaoRepository.findByParticipanteIdComEvento(usuarioId)).thenReturn(inscricoes);
        when(presencaService.calcularFrequencia(anyLong(), anyLong())).thenReturn(50.0);
        List<HistoricoParticipacaoDto> resultado = usuarioService.listarHistoricoParticipacao(usuarioId);
        assertEquals(25, resultado.size());
    }

    @Test
    void certificadoIndisponivelQuandoTemplatesEstaoVazios() {
        Long usuarioId = 1L;
        Evento eventoSemTemplate = new Evento();
        eventoSemTemplate.setId(10L);
        eventoSemTemplate.setTitulo("Evento Sem Template");
        eventoSemTemplate.setDataInicio(LocalDate.of(2026, 10, 1));
        eventoSemTemplate.setCertificadoTemplate(null);
        eventoSemTemplate.setCertificadoTemplateVerso(null);
        Inscricao inscricao = new Inscricao();
        inscricao.setEvento(eventoSemTemplate);
        inscricao.setStatus(StatusInscricao.UTILIZADO);
        when(inscricaoRepository.findByParticipanteIdComEvento(usuarioId)).thenReturn(List.of(inscricao));
        when(presencaService.calcularFrequencia(anyLong(), anyLong())).thenReturn(80.0);
        List<HistoricoParticipacaoDto> resultado = usuarioService.listarHistoricoParticipacao(usuarioId);
        assertEquals(1, resultado.size());
        assertFalse(resultado.get(0).isCertificadoDisponivel());
    }

    @Test
    void frequenciaNaoAplicavelQuandoNaoHaSessoes() {
        Long usuarioId = 1L;
        Evento evento = new Evento();
        evento.setId(10L);
        evento.setTitulo("Evento Sem Sessoes");
        evento.setDataInicio(LocalDate.of(2026, 10, 1));
        evento.setCertificadoTemplate("f.png");
        evento.setCertificadoTemplateVerso("v.png");
        Inscricao inscricao = new Inscricao();
        inscricao.setEvento(evento);
        inscricao.setStatus(StatusInscricao.UTILIZADO);
        when(inscricaoRepository.findByParticipanteIdComEvento(usuarioId)).thenReturn(List.of(inscricao));
        when(presencaService.calcularFrequencia(anyLong(), anyLong())).thenThrow(new RuntimeException("sem sessoes"));
        List<HistoricoParticipacaoDto> resultado = usuarioService.listarHistoricoParticipacao(usuarioId);
        assertEquals(1, resultado.size());
        assertNull(resultado.get(0).getPercentualFrequencia());
    }

    // --- Testes: Histórico de Eventos Organizados ---

    @Test
    void usuarioSemEventosRetornaListaVazia() {
        Long usuarioId = 1L;
        when(eventoRepository.findByOrganizadorId(usuarioId)).thenReturn(List.of());
        List<HistoricoEventosOrganizadosDto> resultado = usuarioService.listarEventosOrganizados(usuarioId);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void usuarioComEventosRetornaEventosCorretos() {
        Long usuarioId = 1L;
        Evento evento = new Evento();
        evento.setId(10L);
        evento.setTitulo("Curso de Java");
        evento.setDataInicio(LocalDate.of(2026, 10, 10));
        evento.setDataTermino(LocalDate.of(2026, 10, 12));
        evento.setModalidade(ModalidadeEvento.PRESENCIAL);
        evento.setNicho(NichoEvento.TECNOLOGIA);
        evento.setCargaHoraria(8);
        evento.setStatus(StatusEvento.PUBLICADO);
        when(eventoRepository.findByOrganizadorId(usuarioId)).thenReturn(List.of(evento));
        when(inscricaoRepository.contarInscricoesValidasDoEvento(10L)).thenReturn(25L);
        List<HistoricoEventosOrganizadosDto> resultado = usuarioService.listarEventosOrganizados(usuarioId);
        assertEquals(1, resultado.size());
        assertEquals(10L, resultado.get(0).getEventoId());
        assertEquals("Curso de Java", resultado.get(0).getTitulo());
        assertEquals("PRESENCIAL", resultado.get(0).getModalidade());
        assertEquals("TECNOLOGIA", resultado.get(0).getNicho());
        assertEquals(8, resultado.get(0).getCargaHoraria());
        assertEquals("PUBLICADO", resultado.get(0).getStatus());
        assertEquals(25L, resultado.get(0).getTotalInscritos());
    }

    @Test
    void eventosDeOutroOrganizadorNaoAparecem() {
        Long usuarioId = 1L;
        when(eventoRepository.findByOrganizadorId(usuarioId)).thenReturn(List.of());
        List<HistoricoEventosOrganizadosDto> resultado = usuarioService.listarEventosOrganizados(usuarioId);
        assertTrue(resultado.isEmpty());
        verify(eventoRepository).findByOrganizadorId(usuarioId);
    }

    @Test
    void eventosDeDiferentesStatusContinuamAparecendo() {
        Long usuarioId = 1L;
        Evento publicado = new Evento();
        publicado.setId(1L);
        publicado.setTitulo("Evento Publicado");
        publicado.setDataInicio(LocalDate.of(2026, 1, 1));
        publicado.setStatus(StatusEvento.PUBLICADO);
        Evento cancelado = new Evento();
        cancelado.setId(2L);
        cancelado.setTitulo("Evento Cancelado");
        cancelado.setDataInicio(LocalDate.of(2026, 2, 1));
        cancelado.setStatus(StatusEvento.CANCELADO);
        Evento encerrado = new Evento();
        encerrado.setId(3L);
        encerrado.setTitulo("Evento Encerrado");
        encerrado.setDataInicio(LocalDate.of(2026, 3, 1));
        encerrado.setStatus(StatusEvento.ENCERRADO);
        when(eventoRepository.findByOrganizadorId(usuarioId))
                .thenReturn(List.of(publicado, cancelado, encerrado));
        when(inscricaoRepository.contarInscricoesValidasDoEvento(anyLong())).thenReturn(0L);
        List<HistoricoEventosOrganizadosDto> resultado = usuarioService.listarEventosOrganizados(usuarioId);
        assertEquals(3, resultado.size());
    }

    @Test
    void eventosOrdenadosDoMaisRecenteParaOMaisAntigo() {
        Long usuarioId = 1L;
        Evento antigo = new Evento();
        antigo.setId(1L);
        antigo.setTitulo("Evento Antigo");
        antigo.setDataInicio(LocalDate.of(2026, 1, 1));
        antigo.setStatus(StatusEvento.ENCERRADO);
        Evento recente = new Evento();
        recente.setId(2L);
        recente.setTitulo("Evento Recente");
        recente.setDataInicio(LocalDate.of(2026, 12, 1));
        recente.setStatus(StatusEvento.PUBLICADO);
        when(eventoRepository.findByOrganizadorId(usuarioId)).thenReturn(List.of(antigo, recente));
        when(inscricaoRepository.contarInscricoesValidasDoEvento(anyLong())).thenReturn(0L);
        List<HistoricoEventosOrganizadosDto> resultado = usuarioService.listarEventosOrganizados(usuarioId);
        assertEquals(2, resultado.size());
        assertEquals("Evento Recente", resultado.get(0).getTitulo());
        assertEquals("Evento Antigo", resultado.get(1).getTitulo());
    }

    @Test
    void totalInscritosUtilizaContarInscricoesValidasDoEvento() {
        Long usuarioId = 1L;
        Evento evento = new Evento();
        evento.setId(10L);
        evento.setTitulo("Evento com Inscritos");
        evento.setDataInicio(LocalDate.of(2026, 10, 1));
        evento.setStatus(StatusEvento.PUBLICADO);
        when(eventoRepository.findByOrganizadorId(usuarioId)).thenReturn(List.of(evento));
        when(inscricaoRepository.contarInscricoesValidasDoEvento(10L)).thenReturn(42L);
        List<HistoricoEventosOrganizadosDto> resultado = usuarioService.listarEventosOrganizados(usuarioId);
        assertEquals(1, resultado.size());
        assertEquals(42L, resultado.get(0).getTotalInscritos());
        verify(inscricaoRepository).contarInscricoesValidasDoEvento(10L);
    }

    @Test
    void exOrganizadorComPerfilParticipanteMantemEventosNoHistorico() {
        Long usuarioId = 1L;
        Evento eventoHistorico = new Evento();
        eventoHistorico.setId(10L);
        eventoHistorico.setTitulo("Evento Antigo do Ex-Organizador");
        eventoHistorico.setDataInicio(LocalDate.of(2025, 6, 1));
        eventoHistorico.setStatus(StatusEvento.ENCERRADO);
        when(eventoRepository.findByOrganizadorId(usuarioId)).thenReturn(List.of(eventoHistorico));
        when(inscricaoRepository.contarInscricoesValidasDoEvento(10L)).thenReturn(5L);
        List<HistoricoEventosOrganizadosDto> resultado = usuarioService.listarEventosOrganizados(usuarioId);
        assertEquals(1, resultado.size());
        assertEquals("Evento Antigo do Ex-Organizador", resultado.get(0).getTitulo());
    }
}