package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import sistema.eventos.dto.AlteracaoPresencaRequestDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.Presenca;
import sistema.eventos.model.Sessao;
import sistema.eventos.model.StatusPresenca;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.repository.PresencaRepository;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.repository.UsuarioRepository;
import sistema.eventos.service.PresencaService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresencaServiceLoteTest {

    private static final Long EVENTO_ID = 10L;
    private static final Long OUTRO_EVENTO_ID = 20L;
    private static final Long ALUNO_ID = 1L;
    private static final String ORGANIZADOR_EMAIL = "dono@seplag.mt.gov.br";

    @Mock
    private PresencaRepository presencaRepository;

    @Mock
    private InscricaoRepository inscricaoRepository;

    @Mock
    private SessaoRepository sessaoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EventoRepository eventoRepository;

    @InjectMocks
    private PresencaService presencaService;

    @Test
    void salvaMultiplasAlteracoesDoMesmoLoteEmUmaUnicaOperacao() {
        Evento evento = eventoDoOrganizador();
        Sessao sessao1 = sessao(100L, evento);
        Sessao sessao2 = sessao(101L, evento);
        Presenca existente = presenca(ALUNO_ID, sessao1, StatusPresenca.PRESENTE);

        prepararAcesso(evento, ORGANIZADOR_EMAIL, Perfil.ROLE_ORGANIZADOR);
        prepararSessoes(sessao1, sessao2);
        when(inscricaoRepository.existsByParticipanteIdAndEventoId(ALUNO_ID, EVENTO_ID)).thenReturn(true);
        when(presencaRepository.findByAlunoIdAndSessaoId(ALUNO_ID, 100L)).thenReturn(Optional.of(existente));
        when(presencaRepository.findByAlunoIdAndSessaoId(ALUNO_ID, 101L)).thenReturn(Optional.empty());
        when(usuarioRepository.getReferenceById(ALUNO_ID)).thenReturn(existente.getAluno());

        int salvas = presencaService.atualizarStatusPresencasEmLote(EVENTO_ID, List.of(
                new AlteracaoPresencaRequestDto(ALUNO_ID, 100L, StatusPresenca.FALTA),
                new AlteracaoPresencaRequestDto(ALUNO_ID, 101L, StatusPresenca.JUSTIFICADA)
        ), ORGANIZADOR_EMAIL);

        assertThat(salvas).isEqualTo(2);
        assertThat(existente.getStatus()).isEqualTo(StatusPresenca.FALTA);

        ArgumentCaptor<List<Presenca>> captor = captorDePresencas();
        verify(presencaRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(1).getStatus()).isEqualTo(StatusPresenca.JUSTIFICADA);
        assertThat(captor.getValue().get(1).getSessao().getId()).isEqualTo(101L);
        assertThat(captor.getValue()).allMatch(p -> ORGANIZADOR_EMAIL.equals(p.getAlteradoPor()));
    }

    @Test
    void aplicaTransicoesPresenteFaltaJustificadaEmSessoesDiferentes() {
        Evento evento = eventoDoOrganizador();
        Sessao sessao1 = sessao(100L, evento);
        Sessao sessao2 = sessao(101L, evento);
        Sessao sessao3 = sessao(102L, evento);
        Presenca presente = presenca(ALUNO_ID, sessao1, StatusPresenca.PRESENTE);
        Presenca falta = presenca(ALUNO_ID, sessao2, StatusPresenca.FALTA);
        Presenca justificada = presenca(ALUNO_ID, sessao3, StatusPresenca.FALTA);

        prepararAcesso(evento, ORGANIZADOR_EMAIL, Perfil.ROLE_ORGANIZADOR);
        prepararSessoes(sessao1, sessao2, sessao3);
        when(inscricaoRepository.existsByParticipanteIdAndEventoId(ALUNO_ID, EVENTO_ID)).thenReturn(true);
        when(presencaRepository.findByAlunoIdAndSessaoId(ALUNO_ID, 100L)).thenReturn(Optional.of(presente));
        when(presencaRepository.findByAlunoIdAndSessaoId(ALUNO_ID, 101L)).thenReturn(Optional.of(falta));
        when(presencaRepository.findByAlunoIdAndSessaoId(ALUNO_ID, 102L)).thenReturn(Optional.of(justificada));

        presencaService.atualizarStatusPresencasEmLote(EVENTO_ID, List.of(
                new AlteracaoPresencaRequestDto(ALUNO_ID, 100L, StatusPresenca.FALTA),
                new AlteracaoPresencaRequestDto(ALUNO_ID, 101L, StatusPresenca.PRESENTE),
                new AlteracaoPresencaRequestDto(ALUNO_ID, 102L, StatusPresenca.JUSTIFICADA)
        ), ORGANIZADOR_EMAIL);

        assertThat(presente.getStatus()).isEqualTo(StatusPresenca.FALTA);
        assertThat(falta.getStatus()).isEqualTo(StatusPresenca.PRESENTE);
        assertThat(justificada.getStatus()).isEqualTo(StatusPresenca.JUSTIFICADA);

        ArgumentCaptor<List<Presenca>> captor = captorDePresencas();
        verify(presencaRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
    }

    @Test
    void rejeitaSessaoDeOutroEventoESalvaNada() {
        Evento evento = eventoDoOrganizador();
        Evento outroEvento = new Evento();
        outroEvento.setId(OUTRO_EVENTO_ID);
        Sessao sessaoValida = sessao(100L, evento);
        Sessao sessaoOutroEvento = sessao(101L, outroEvento);

        prepararAcesso(evento, ORGANIZADOR_EMAIL, Perfil.ROLE_ORGANIZADOR);
        prepararSessoes(sessaoValida, sessaoOutroEvento);
        when(inscricaoRepository.existsByParticipanteIdAndEventoId(ALUNO_ID, EVENTO_ID)).thenReturn(true);

        assertThatThrownBy(() -> presencaService.atualizarStatusPresencasEmLote(EVENTO_ID, List.of(
                new AlteracaoPresencaRequestDto(ALUNO_ID, 100L, StatusPresenca.PRESENTE),
                new AlteracaoPresencaRequestDto(ALUNO_ID, 101L, StatusPresenca.PRESENTE)
        ), ORGANIZADOR_EMAIL))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("não pertence a este evento");

        verify(presencaRepository, never()).saveAll(any());
        verify(presencaRepository, never()).findByAlunoIdAndSessaoId(anyLong(), anyLong());
    }

    @Test
    void rejeitaParticipanteSemInscricaoNoEventoESalvaNada() {
        Evento evento = eventoDoOrganizador();
        Sessao sessao = sessao(100L, evento);

        prepararAcesso(evento, ORGANIZADOR_EMAIL, Perfil.ROLE_ORGANIZADOR);
        prepararSessoes(sessao);
        when(inscricaoRepository.existsByParticipanteIdAndEventoId(ALUNO_ID, EVENTO_ID)).thenReturn(false);

        assertThatThrownBy(() -> presencaService.atualizarStatusPresencasEmLote(EVENTO_ID, List.of(
                new AlteracaoPresencaRequestDto(ALUNO_ID, 100L, StatusPresenca.PRESENTE)
        ), ORGANIZADOR_EMAIL))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("não pertence a este evento");

        verify(presencaRepository, never()).saveAll(any());
    }

    @Test
    void rejeitaDuplicidadeDoMesmoParticipanteNaMesmaSessao() {
        Evento evento = eventoDoOrganizador();
        Sessao sessao = sessao(100L, evento);

        prepararAcesso(evento, ORGANIZADOR_EMAIL, Perfil.ROLE_ORGANIZADOR);
        prepararSessoes(sessao);
        when(inscricaoRepository.existsByParticipanteIdAndEventoId(ALUNO_ID, EVENTO_ID)).thenReturn(true);

        assertThatThrownBy(() -> presencaService.atualizarStatusPresencasEmLote(EVENTO_ID, List.of(
                new AlteracaoPresencaRequestDto(ALUNO_ID, 100L, StatusPresenca.PRESENTE),
                new AlteracaoPresencaRequestDto(ALUNO_ID, 100L, StatusPresenca.FALTA)
        ), ORGANIZADOR_EMAIL))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("duplicadas");

        verify(presencaRepository, never()).saveAll(any());
    }

    @Test
    void organizadorDeOutroEventoNaoPodeSalvarLote() {
        Evento evento = eventoDoOrganizador();
        prepararAcesso(evento, "outro@seplag.mt.gov.br", Perfil.ROLE_ORGANIZADOR);

        assertThatThrownBy(() -> presencaService.atualizarStatusPresencasEmLote(EVENTO_ID, List.of(
                new AlteracaoPresencaRequestDto(ALUNO_ID, 100L, StatusPresenca.PRESENTE)
        ), "outro@seplag.mt.gov.br"))
                .isInstanceOf(AccessDeniedException.class);

        verify(presencaRepository, never()).saveAll(any());
    }

    @Test
    void superAdminPodeSalvarLoteDeQualquerEvento() {
        Evento evento = eventoDoOrganizador();
        Sessao sessao = sessao(100L, evento);
        Presenca existente = presenca(ALUNO_ID, sessao, StatusPresenca.FALTA);

        prepararAcesso(evento, "admin@seplag.mt.gov.br", Perfil.ROLE_SUPER_ADMIN);
        prepararSessoes(sessao);
        when(inscricaoRepository.existsByParticipanteIdAndEventoId(ALUNO_ID, EVENTO_ID)).thenReturn(true);
        when(presencaRepository.findByAlunoIdAndSessaoId(ALUNO_ID, 100L)).thenReturn(Optional.of(existente));

        int salvas = presencaService.atualizarStatusPresencasEmLote(EVENTO_ID, List.of(
                new AlteracaoPresencaRequestDto(ALUNO_ID, 100L, StatusPresenca.PRESENTE)
        ), "admin@seplag.mt.gov.br");

        assertThat(salvas).isEqualTo(1);
        assertThat(existente.getStatus()).isEqualTo(StatusPresenca.PRESENTE);
    }

    @Test
    void rejeitaLoteVazio() {
        Evento evento = eventoDoOrganizador();
        prepararAcesso(evento, ORGANIZADOR_EMAIL, Perfil.ROLE_ORGANIZADOR);

        assertThatThrownBy(() -> presencaService.atualizarStatusPresencasEmLote(
                EVENTO_ID, List.of(), ORGANIZADOR_EMAIL))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Nenhuma alteração");

        verify(presencaRepository, never()).saveAll(any());
    }

    @Test
    void operacaoEmLoteEhTransacional() throws NoSuchMethodException {
        var metodo = PresencaService.class.getMethod(
                "atualizarStatusPresencasEmLote", Long.class, List.class, String.class);

        assertThat(metodo.isAnnotationPresent(Transactional.class)).isTrue();
    }

    private void prepararAcesso(Evento evento, String email, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(99L);
        usuario.setEmail(email);
        usuario.setPerfil(perfil);

        when(eventoRepository.findByIdComOrganizador(EVENTO_ID)).thenReturn(Optional.of(evento));
        when(usuarioRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(usuario));
    }

    private void prepararSessoes(Sessao... sessoes) {
        for (Sessao sessao : sessoes) {
            when(sessaoRepository.findById(sessao.getId())).thenReturn(Optional.of(sessao));
        }
    }

    private Evento eventoDoOrganizador() {
        Usuario organizador = new Usuario();
        organizador.setId(99L);
        organizador.setEmail(ORGANIZADOR_EMAIL);
        organizador.setPerfil(Perfil.ROLE_ORGANIZADOR);

        Evento evento = new Evento();
        evento.setId(EVENTO_ID);
        evento.setTitulo("Evento de Teste");
        evento.setOrganizador(organizador);
        return evento;
    }

    private Sessao sessao(Long id, Evento evento) {
        Sessao sessao = new Sessao();
        sessao.setId(id);
        sessao.setEvento(evento);
        return sessao;
    }

    private Presenca presenca(Long alunoId, Sessao sessao, StatusPresenca status) {
        Usuario aluno = new Usuario();
        aluno.setId(alunoId);
        aluno.setNome("Participante");

        Presenca presenca = new Presenca();
        presenca.setAluno(aluno);
        presenca.setSessao(sessao);
        presenca.setStatus(status);
        return presenca;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<Presenca>> captorDePresencas() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
