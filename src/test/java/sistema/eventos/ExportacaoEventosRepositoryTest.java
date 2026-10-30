package sistema.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import sistema.eventos.dto.UsuarioEventoTituloProjection;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida, no banco, as consultas em lote usadas pela exportação do Super Admin:
 * participação somente com inscrição UTILIZADO e organização pelo relacionamento
 * real Evento.organizador.
 */
@DataJpaTest
class ExportacaoEventosRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EventoRepository eventoRepository;

    @Autowired
    private InscricaoRepository inscricaoRepository;

    private Usuario organizadorAna;
    private Usuario organizadorBruno;
    private Usuario participantePaula;
    private Usuario participanteQuim;
    private Evento java;
    private Evento spring;
    private Evento congresso;

    @BeforeEach
    void prepararDados() {
        organizadorAna = usuarioRepository.save(criarUsuario("Ana Organizadora", "ana.organizadora@seplag.mt.gov.br", Perfil.ROLE_ORGANIZADOR));
        organizadorBruno = usuarioRepository.save(criarUsuario("Bruno Organizador", "bruno.organizador@seplag.mt.gov.br", Perfil.ROLE_ORGANIZADOR));
        participantePaula = usuarioRepository.save(criarUsuario("Paula Participante", "paula@seplag.mt.gov.br", Perfil.ROLE_PARTICIPANTE));
        participanteQuim = usuarioRepository.save(criarUsuario("Quim Participante", "quim@seplag.mt.gov.br", Perfil.ROLE_PARTICIPANTE));

        java = eventoRepository.save(criarEvento("Workshop de Java", organizadorAna));
        spring = eventoRepository.save(criarEvento("Curso de Spring Boot", organizadorAna));
        congresso = eventoRepository.save(criarEvento("Congresso de Tecnologia", organizadorBruno));

        // Paula: participação validada em Java, inscrição ainda ATIVO em Spring e CANCELADO no Congresso.
        inscricaoRepository.save(criarInscricao(participantePaula, java, StatusInscricao.UTILIZADO));
        inscricaoRepository.save(criarInscricao(participantePaula, spring, StatusInscricao.ATIVO));
        inscricaoRepository.save(criarInscricao(participantePaula, congresso, StatusInscricao.CANCELADO));
        // Quim: participação validada no Congresso.
        inscricaoRepository.save(criarInscricao(participanteQuim, congresso, StatusInscricao.UTILIZADO));
    }

    @Test
    void participadosRetornaSomenteInscricoesUtilizadas() {
        Map<Long, List<String>> porUsuario = agrupar(inscricaoRepository
                .findTitulosEventosPorParticipantes(
                        List.of(participantePaula.getId(), participanteQuim.getId()),
                        StatusInscricao.UTILIZADO));

        assertThat(porUsuario.get(participantePaula.getId()))
                .containsExactly("Workshop de Java")
                .doesNotContain("Curso de Spring Boot", "Congresso de Tecnologia");
        assertThat(porUsuario.get(participanteQuim.getId()))
                .containsExactly("Congresso de Tecnologia");
    }

    @Test
    void participadosRetornaDataInicioEDataTerminoDoEvento() {
        List<UsuarioEventoTituloProjection> projecoes = inscricaoRepository
                .findTitulosEventosPorParticipantes(List.of(participantePaula.getId()), StatusInscricao.UTILIZADO);

        assertThat(projecoes).singleElement().satisfies(projecao -> {
            assertThat(projecao.getTitulo()).isEqualTo("Workshop de Java");
            assertThat(projecao.getDataInicio()).isEqualTo(java.getDataInicio());
            assertThat(projecao.getDataTermino()).isEqualTo(java.getDataTermino());
        });
    }

    @Test
    void organizadosRetornaDataInicioEDataTerminoDoEvento() {
        List<UsuarioEventoTituloProjection> projecoes = eventoRepository
                .findTitulosEventosOrganizadosPorUsuarios(List.of(organizadorBruno.getId()));

        assertThat(projecoes).singleElement().satisfies(projecao -> {
            assertThat(projecao.getTitulo()).isEqualTo("Congresso de Tecnologia");
            assertThat(projecao.getDataInicio()).isEqualTo(congresso.getDataInicio());
            assertThat(projecao.getDataTermino()).isEqualTo(congresso.getDataTermino());
        });
    }

    @Test
    void inscricaoAtivaOuCanceladaNaoApareceComoParticipacao() {
        List<String> titulos = inscricaoRepository
                .findTitulosEventosPorParticipantes(List.of(participantePaula.getId()), StatusInscricao.UTILIZADO)
                .stream()
                .map(UsuarioEventoTituloProjection::getTitulo)
                .toList();

        assertThat(titulos)
                .containsExactly("Workshop de Java")
                .doesNotContain("Curso de Spring Boot")
                .doesNotContain("Congresso de Tecnologia");
    }

    @Test
    void organizadosUsaORelacionamentoRealComOrganizador() {
        Map<Long, List<String>> porUsuario = agrupar(eventoRepository
                .findTitulosEventosOrganizadosPorUsuarios(
                        List.of(organizadorAna.getId(), organizadorBruno.getId())));

        assertThat(porUsuario.get(organizadorAna.getId()))
                .containsExactlyInAnyOrder("Workshop de Java", "Curso de Spring Boot");
        assertThat(porUsuario.get(organizadorBruno.getId()))
                .containsExactly("Congresso de Tecnologia");
        // Participantes não organizam nada, mesmo tendo inscrição em eventos.
        assertThat(porUsuario).doesNotContainKeys(participantePaula.getId(), participanteQuim.getId());
    }

    @Test
    void usuariosSemEventosNaoAparecemNoAgrupamento() {
        Usuario semEventos = usuarioRepository.save(
                criarUsuario("Sem Eventos", "sem.eventos@seplag.mt.gov.br", Perfil.ROLE_PARTICIPANTE));

        assertThat(inscricaoRepository.findTitulosEventosPorParticipantes(
                List.of(semEventos.getId()), StatusInscricao.UTILIZADO)).isEmpty();
        assertThat(eventoRepository.findTitulosEventosOrganizadosPorUsuarios(
                List.of(semEventos.getId()))).isEmpty();
    }

    private Map<Long, List<String>> agrupar(List<UsuarioEventoTituloProjection> projecoes) {
        return projecoes.stream().collect(Collectors.groupingBy(
                UsuarioEventoTituloProjection::getUsuarioId,
                Collectors.mapping(UsuarioEventoTituloProjection::getTitulo, Collectors.toList())));
    }

    private Usuario criarUsuario(String nome, String email, Perfil perfil) {
        return Usuario.builder()
                .nome(nome)
                .email(email)
                .senha("senha-segura")
                .perfil(perfil)
                .ativo(true)
                .build();
    }

    private Evento criarEvento(String titulo, Usuario organizador) {
        return Evento.builder()
                .titulo(titulo)
                .descricao("Descrição do evento " + titulo)
                .dataInicio(LocalDate.of(2026, 3, 1))
                .dataTermino(LocalDate.of(2026, 3, 5))
                .vagasTotais(50)
                .vagasRestantes(50)
                .status(StatusEvento.PUBLICADO)
                .local("Auditório SEPLAG-MT")
                .modalidade(ModalidadeEvento.PRESENCIAL)
                .nicho(NichoEvento.TECNOLOGIA)
                .organizador(organizador)
                .build();
    }

    private Inscricao criarInscricao(Usuario participante, Evento evento, StatusInscricao status) {
        return Inscricao.builder()
                .participante(participante)
                .evento(evento)
                .status(status)
                .build();
    }
}
