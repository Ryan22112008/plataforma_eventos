package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import sistema.eventos.config.RelogioConfig;
import sistema.eventos.controller.HomeController;
import sistema.eventos.model.Evento;
import sistema.eventos.model.ModalidadeEvento;
import sistema.eventos.model.NichoEvento;
import sistema.eventos.model.StatusEvento;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.SolicitacaoRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HomeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(HomeControllerFiltrosWebTest.RelogioFixo.class)
class HomeControllerFiltrosWebTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 15);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventoRepository eventoRepository;

    @MockBean
    private SolicitacaoRepository solicitacaoRepository;

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        Clock relogioAplicacao() {
            // 12:00 UTC = 08:00 em America/Cuiaba, mesmo dia 15/11/2026.
            return Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), RelogioConfig.ZONA_APLICACAO);
        }
    }

    @Test
    void deveRenderizarBuscaComTodosOsFiltrosDentroDoFormulario() throws Exception {
        when(eventoRepository.buscarEventosPublicos(any(), anyBoolean(), anyString(), any(), any(),
                any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        String html = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("role=\"search\"")))
                .andExpect(content().string(containsString("aria-label=\"Filtros de busca\"")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        int inicio = html.indexOf("class=\"seplag-public-search\"");
        int fim = html.indexOf("</form>", inicio);
        assertThat(inicio).isGreaterThanOrEqualTo(0);
        assertThat(fim).isGreaterThan(inicio);

        String formulario = html.substring(inicio, fim);
        assertThat(formulario).contains(
                "name=\"busca\"", "id=\"busca\"",
                "name=\"nicho\"", "id=\"nicho\"",
                "name=\"modalidade\"", "id=\"modalidade\"",
                "name=\"data\"", "id=\"data\"",
                "name=\"vigencia\"", "id=\"vigencia\"",
                "type=\"submit\"");
        assertThat(formulario).doesNotContain("<form");
    }

    @Test
    void deveRenderizarOsControlesDeDataEVigencia() throws Exception {
        when(eventoRepository.buscarEventosPublicos(any(), anyBoolean(), anyString(), any(), any(),
                any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("for=\"data\"")))
                .andExpect(content().string(containsString("name=\"data\"")))
                .andExpect(content().string(containsString("for=\"vigencia\"")))
                .andExpect(content().string(containsString("<option value=\"\" selected=\"selected\">Todas</option>")))
                .andExpect(content().string(containsString("<option value=\"FUTUROS\">Futuros</option>")))
                .andExpect(content().string(containsString("<option value=\"EM_ANDAMENTO\">Em andamento</option>")))
                .andExpect(content().string(containsString("<option value=\"ENCERRADOS\">Encerrados</option>")))
                .andExpect(content().string(not(containsString("\\u00"))));
    }

    @Test
    void deveEnviarDataVigenciaEHojeParaOConsultorioDoBanco() throws Exception {
        when(eventoRepository.buscarEventosPublicos(any(), anyBoolean(), anyString(), any(), any(),
                any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/")
                        .param("busca", "Java")
                        .param("nicho", "TECNOLOGIA")
                        .param("modalidade", "PRESENCIAL")
                        .param("data", "2026-11-15")
                        .param("vigencia", "FUTUROS"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filtroData", "2026-11-15"))
                .andExpect(model().attribute("filtroVigencia", "FUTUROS"));

        ArgumentCaptor<LocalDate> data = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<String> vigencia = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDate> hoje = ArgumentCaptor.forClass(LocalDate.class);
        verify(eventoRepository).buscarEventosPublicos(
                eq(StatusEvento.PUBLICADO), eq(true), eq("Java"),
                eq(NichoEvento.TECNOLOGIA), eq(ModalidadeEvento.PRESENCIAL),
                data.capture(), vigencia.capture(), hoje.capture(), any(Pageable.class));

        assertThat(data.getValue()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(vigencia.getValue()).isEqualTo("FUTUROS");
        assertThat(hoje.getValue()).isEqualTo(HOJE);
    }

    @Test
    void deveManterComportamentoAtualQuandoDataEVigenciaEstaoAusentes() throws Exception {
        when(eventoRepository.buscarEventosPublicos(any(), anyBoolean(), anyString(), any(), any(),
                any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filtroData", ""))
                .andExpect(model().attribute("filtroVigencia", ""));

        ArgumentCaptor<LocalDate> data = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<String> vigencia = ArgumentCaptor.forClass(String.class);
        verify(eventoRepository).buscarEventosPublicos(
                eq(StatusEvento.PUBLICADO), eq(false), eq(""), any(), any(),
                data.capture(), vigencia.capture(), eq(HOJE), any(Pageable.class));

        assertThat(data.getValue()).isNull();
        assertThat(vigencia.getValue()).isNull();
    }

    @Test
    void naoDeveConsultarBancoQuandoADataForInvalida() throws Exception {
        mockMvc.perform(get("/").param("data", "15/11/2026"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhum curso localizado")));

        verify(eventoRepository, never()).buscarEventosPublicos(any(), anyBoolean(), anyString(), any(),
                any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void devePreservarTodosOsFiltrosNosLinksDePaginacao() throws Exception {
        Page<Evento> pagina = new PageImpl<>(List.of(eventoPublicado()), PageRequest.of(1, 20), 45);
        when(eventoRepository.buscarEventosPublicos(any(), anyBoolean(), anyString(), any(), any(),
                any(), any(), any(), any(Pageable.class))).thenReturn(pagina);

        mockMvc.perform(get("/")
                        .param("busca", "Java")
                        .param("nicho", "TECNOLOGIA")
                        .param("modalidade", "PRESENCIAL")
                        .param("data", "2026-11-15")
                        .param("vigencia", "FUTUROS")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("busca=Java")))
                .andExpect(content().string(containsString("nicho=TECNOLOGIA")))
                .andExpect(content().string(containsString("modalidade=PRESENCIAL")))
                .andExpect(content().string(containsString("data=2026-11-15")))
                .andExpect(content().string(containsString("vigencia=FUTUROS")))
                .andExpect(content().string(containsString("page=2")))
                .andExpect(content().string(containsString("page=0")))
                .andExpect(content().string(containsString("href=\"/eventos/1\"")))
                .andExpect(content().string(not(containsString("/home/eventos/1"))));
    }

    private Evento eventoPublicado() {
        Evento evento = new Evento();
        evento.setId(1L);
        evento.setTitulo("Java Avançado");
        evento.setDescricao("Curso de Java.");
        evento.setDataInicio(LocalDate.of(2026, 11, 15));
        evento.setDataTermino(LocalDate.of(2026, 11, 16));
        evento.setStatus(StatusEvento.PUBLICADO);
        evento.setVagasTotais(50);
        evento.setVagasRestantes(50);
        evento.setLocal("Auditório SEPLAG");
        return evento;
    }
}
