package sistema.eventos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import sistema.eventos.model.Evento;
import sistema.eventos.model.ModalidadeEvento;
import sistema.eventos.model.NichoEvento;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.StatusEvento;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.UsuarioRepository;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filtros de data e vigência da busca pública aplicados no banco (JPQL).
 * A data de referência é sempre passada como parâmetro, então o teste não depende do relógio real.
 */
@DataJpaTest
class EventoRepositoryFiltrosTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 15);
    private static final Pageable PRIMEIRA_PAGINA = PageRequest.of(0, 20,
            Sort.by(Sort.Order.asc("dataInicio"), Sort.Order.asc("id")));

    @Autowired
    private EventoRepository eventoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario organizador;

    @BeforeEach
    void prepararDados() {
        organizador = usuarioRepository.save(Usuario.builder()
                .nome("Organizador de Teste")
                .email("organizador.filtros@seplag.mt.gov.br")
                .senha("senha-segura")
                .perfil(Perfil.ROLE_ORGANIZADOR)
                .ativo(true)
                .build());

        // Começa hoje (em andamento)
        evento("Java Avançado", LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 16),
                NichoEvento.TECNOLOGIA, ModalidadeEvento.PRESENCIAL);
        // Começou e ainda não terminou
        evento("Java Básico", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 20),
                NichoEvento.TECNOLOGIA, ModalidadeEvento.ONLINE);
        // Já terminou
        evento("Gestão Pública", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10),
                NichoEvento.GESTAO_PUBLICA, ModalidadeEvento.PRESENCIAL);
        // Ainda não começou
        evento("Saúde na Prática", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 25),
                NichoEvento.SAUDE, ModalidadeEvento.PRESENCIAL);
        // Já terminou
        evento("Java Encerrado", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5),
                NichoEvento.TECNOLOGIA, ModalidadeEvento.PRESENCIAL);
        // Termina hoje (ainda em andamento)
        evento("Inovação Aberta", LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15),
                NichoEvento.OUTROS, ModalidadeEvento.HIBRIDO);
        // Ainda não começou
        evento("Oficina de Dados", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 17),
                NichoEvento.TECNOLOGIA, ModalidadeEvento.PRESENCIAL);

        // Rascunho na mesma data: nunca deve aparecer na busca pública
        Evento rascunho = evento("Java Rascunho", HOJE, LocalDate.of(2026, 11, 16),
                NichoEvento.TECNOLOGIA, ModalidadeEvento.PRESENCIAL);
        rascunho.setStatus(StatusEvento.RASCUNHO);
        eventoRepository.save(rascunho);
    }

    @Test
    void semFiltrosMantemComportamentoAtual() {
        Page<Evento> pagina = buscar(null, null, null, null);

        assertThat(pagina.getTotalElements()).isEqualTo(7);
        assertThat(titulos(pagina)).doesNotContain("Java Rascunho");
    }

    @Test
    void filtraPeloDiaDaDataDeInicio() {
        Page<Evento> pagina = buscar(LocalDate.of(2026, 11, 15), null, null, null);

        assertThat(titulos(pagina)).containsExactly("Java Avançado");
    }

    @Test
    void filtraPorDataConsiderandoEventoQueTerminaNaqueleDia() {
        Page<Evento> pagina = buscar(LocalDate.of(2026, 11, 14), null, null, null);

        assertThat(titulos(pagina)).containsExactly("Inovação Aberta");
    }

    @Test
    void retornaListaVaziaQuandoNaoHaEventoNaData() {
        Page<Evento> pagina = buscar(LocalDate.of(2026, 12, 1), null, null, null);

        assertThat(pagina.getContent()).isEmpty();
        assertThat(pagina.getTotalElements()).isZero();
    }

    @Test
    void vigenciaFuturosTrazSomenteEventosQueAindaNaoComecaram() {
        Page<Evento> pagina = buscar(null, "FUTUROS", null, null);

        assertThat(titulos(pagina)).containsExactlyInAnyOrder("Saúde na Prática", "Oficina de Dados");
    }

    @Test
    void vigenciaEmAndamentoIncluiEventosQueComecamOuTerminamHoje() {
        Page<Evento> pagina = buscar(null, "EM_ANDAMENTO", null, null);

        assertThat(titulos(pagina)).containsExactlyInAnyOrder(
                "Java Avançado", "Java Básico", "Inovação Aberta");
    }

    @Test
    void vigenciaEncerradosTrazSomenteEventosComTerminoAnteriorAHoje() {
        Page<Evento> pagina = buscar(null, "ENCERRADOS", null, null);

        assertThat(titulos(pagina)).containsExactlyInAnyOrder("Gestão Pública", "Java Encerrado");
    }

    @Test
    void vigenciaAusenteNaoAplicaFiltro() {
        Page<Evento> pagina = buscar(null, null, null, null);

        assertThat(pagina.getTotalElements()).isEqualTo(7);
    }

    @Test
    void combinaTextoComData() {
        Page<Evento> pagina = buscar(LocalDate.of(2026, 11, 15), null, "Java", null);

        assertThat(titulos(pagina)).containsExactly("Java Avançado");
    }

    @Test
    void combinaNichoComVigencia() {
        Page<Evento> pagina = buscar(null, "ENCERRADOS", null, NichoEvento.TECNOLOGIA);

        assertThat(titulos(pagina)).containsExactly("Java Encerrado");
    }

    @Test
    void combinaModalidadeComData() {
        Page<Evento> pagina = buscar(LocalDate.of(2026, 11, 20), "FUTUROS", null, null,
                ModalidadeEvento.PRESENCIAL);

        assertThat(titulos(pagina)).containsExactly("Saúde na Prática");
    }

    @Test
    void combinaDataComVigencia() {
        Page<Evento> pagina = buscar(LocalDate.of(2026, 11, 10), "EM_ANDAMENTO", null, null);

        assertThat(titulos(pagina)).containsExactly("Java Básico");
    }

    @Test
    void combinaTodosOsFiltros() {
        Page<Evento> pagina = buscar(LocalDate.of(2026, 11, 10), "EM_ANDAMENTO", "Java",
                NichoEvento.TECNOLOGIA, ModalidadeEvento.ONLINE);

        assertThat(titulos(pagina)).containsExactly("Java Básico");
    }

    @Test
    void combinacaoSemResultadoRetornaListaVazia() {
        Page<Evento> pagina = buscar(LocalDate.of(2026, 11, 15), "ENCERRADOS", "Java",
                NichoEvento.TECNOLOGIA, ModalidadeEvento.PRESENCIAL);

        assertThat(pagina.getContent()).isEmpty();
        assertThat(pagina.getTotalElements()).isZero();
    }

    private Page<Evento> buscar(LocalDate data, String vigencia, String busca, NichoEvento nicho) {
        return buscar(data, vigencia, busca, nicho, null);
    }

    private Page<Evento> buscar(LocalDate data, String vigencia, String busca, NichoEvento nicho,
                                ModalidadeEvento modalidade) {
        boolean temBusca = busca != null && !busca.isBlank();
        return eventoRepository.buscarEventosPublicos(
                StatusEvento.PUBLICADO,
                temBusca,
                temBusca ? busca : "",
                nicho,
                modalidade,
                data,
                vigencia,
                HOJE,
                PRIMEIRA_PAGINA);
    }

    private List<String> titulos(Page<Evento> pagina) {
        return pagina.getContent().stream().map(Evento::getTitulo).toList();
    }

    private Evento evento(String titulo, LocalDate inicio, LocalDate termino,
                          NichoEvento nicho, ModalidadeEvento modalidade) {
        Evento evento = Evento.builder()
                .titulo(titulo)
                .descricao("Descrição do evento " + titulo)
                .dataInicio(inicio)
                .dataTermino(termino)
                .vagasTotais(100)
                .vagasRestantes(100)
                .status(StatusEvento.PUBLICADO)
                .local("Auditório SEPLAG-MT")
                .modalidade(modalidade)
                .nicho(nicho)
                .cargaHoraria(8)
                .organizador(organizador)
                .build();
        return eventoRepository.save(evento);
    }
}
