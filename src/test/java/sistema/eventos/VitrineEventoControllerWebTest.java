package sistema.eventos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import sistema.eventos.controller.VitrineEventoController;
import sistema.eventos.dto.AvaliacaoEventoResponseDto;
import sistema.eventos.dto.AvaliacaoResumoDto;
import sistema.eventos.model.Evento;
import sistema.eventos.model.StatusEvento;
import sistema.eventos.model.Usuario;
import sistema.eventos.service.AvaliacaoEventoService;
import sistema.eventos.service.EventoService;
import sistema.eventos.service.UsuarioService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VitrineEventoController.class)
@AutoConfigureMockMvc(addFilters = false)
class VitrineEventoControllerWebTest {

    private static final Long EVENTO_ID = 1L;
    private static final String EMAIL_PARTICIPANTE = "participante@seplag.mt.gov.br";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventoService eventoService;

    @MockBean
    private AvaliacaoEventoService avaliacaoEventoService;

    @MockBean
    private UsuarioService usuarioService;

    @AfterEach
    void limparContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveRenderizarResumoEListaDeAvaliacoesParaVisitante() throws Exception {
        when(eventoService.buscarPorId(EVENTO_ID)).thenReturn(eventoPublicado(2L));
        when(avaliacaoEventoService.obterResumo(EVENTO_ID)).thenReturn(resumoComAvaliacoes(4.8, 2));
        when(avaliacaoEventoService.listarDoEvento(eq(EVENTO_ID), any(Pageable.class)))
                .thenReturn(paginaDeAvaliacoes());

        mockMvc.perform(get("/eventos/{id}", EVENTO_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("Avaliações")))
                .andExpect(content().string(containsString("4,8")))
                .andExpect(content().string(containsString("2 avaliações")))
                .andExpect(content().string(containsString("Maria Silva")))
                .andExpect(content().string(containsString("Excelente curso.")))
                .andExpect(content().string(containsString("Avaliações dos participantes")))
                .andExpect(content().string(containsString("Entre na plataforma")))
                .andExpect(content().string(not(containsString("\\u00"))));
    }

    @Test
    void deveRenderizarFormularioParaParticipanteElegivel() throws Exception {
        autenticar(1L, "Maria Silva");
        when(eventoService.buscarPorId(EVENTO_ID)).thenReturn(eventoPublicado(2L));
        when(avaliacaoEventoService.obterResumo(EVENTO_ID)).thenReturn(resumoSemAvaliacoes());
        when(avaliacaoEventoService.listarDoEvento(eq(EVENTO_ID), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(avaliacaoEventoService.buscarDoUsuarioNoEvento(EVENTO_ID, 1L)).thenReturn(Optional.empty());
        when(avaliacaoEventoService.podeAvaliar(any(Evento.class), eq(1L))).thenReturn(true);

        mockMvc.perform(get("/eventos/{id}", EVENTO_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ainda não há avaliações.")))
                .andExpect(content().string(containsString("Avalie este evento")))
                .andExpect(content().string(containsString("name=\"nota\" value=\"5\"")))
                .andExpect(content().string(containsString("name=\"nota\" value=\"1\"")))
                .andExpect(content().string(containsString("Enviar avaliação")))
                .andExpect(content().string(containsString("Comentário")))
                .andExpect(content().string(containsString("Máximo de 1000 caracteres.")))
                .andExpect(content().string(not(containsString("Apenas participantes com presença confirmada"))));
    }

    @Test
    void deveRenderizarAvaliacaoPropriaComEdicaoEExclusao() throws Exception {
        autenticar(1L, "Maria Silva");
        when(eventoService.buscarPorId(EVENTO_ID)).thenReturn(eventoPublicado(2L));
        when(avaliacaoEventoService.obterResumo(EVENTO_ID)).thenReturn(resumoComAvaliacoes(4.0, 1));
        when(avaliacaoEventoService.listarDoEvento(eq(EVENTO_ID), any(Pageable.class)))
                .thenReturn(paginaDeAvaliacoes());
        when(avaliacaoEventoService.buscarDoUsuarioNoEvento(EVENTO_ID, 1L))
                .thenReturn(Optional.of(avaliacaoDoUsuario()));

        mockMvc.perform(get("/eventos/{id}", EVENTO_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sua avaliação")))
                .andExpect(content().string(containsString("Você já avaliou este evento.")))
                .andExpect(content().string(containsString("id=\"editar-nota-4\"")))
                .andExpect(content().string(containsString("checked=\"checked\"")))
                .andExpect(content().string(containsString("Editar avaliação")))
                .andExpect(content().string(containsString("Salvar alterações")))
                .andExpect(content().string(containsString("Excluir minha avaliação")))
                .andExpect(content().string(not(containsString("Avalie este evento"))));
    }

    @Test
    void naoDeveRenderizarFormularioParaOrganizador() throws Exception {
        autenticar(2L, "Organizador");
        when(eventoService.buscarPorId(EVENTO_ID)).thenReturn(eventoPublicado(2L));
        when(avaliacaoEventoService.obterResumo(EVENTO_ID)).thenReturn(resumoSemAvaliacoes());
        when(avaliacaoEventoService.listarDoEvento(eq(EVENTO_ID), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(avaliacaoEventoService.buscarDoUsuarioNoEvento(EVENTO_ID, 2L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/eventos/{id}", EVENTO_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Você organiza este evento e não pode avaliá-lo.")))
                .andExpect(content().string(not(containsString("Avalie este evento"))))
                .andExpect(content().string(not(containsString("Enviar avaliação"))));
    }

    @Test
    void deveRenderizarBannerLocalDoEventoNoHero() throws Exception {
        Evento evento = eventoPublicado(2L);
        evento.setBannerUrl("banner-do-evento.jpg");
        when(eventoService.buscarPorId(EVENTO_ID)).thenReturn(evento);
        when(avaliacaoEventoService.obterResumo(EVENTO_ID)).thenReturn(resumoSemAvaliacoes());
        when(avaliacaoEventoService.listarDoEvento(eq(EVENTO_ID), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/eventos/{id}", EVENTO_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Curso de Teste")))
                .andExpect(content().string(containsString("url(&#39;/imagens-eventos/banner-do-evento.jpg&#39;)")))
                .andExpect(content().string(containsString("Realizar Inscrição")))
                .andExpect(content().string(containsString("Sobre o evento")))
                .andExpect(content().string(containsString("Informações do evento")))
                .andExpect(content().string(containsString("Local do evento")))
                .andExpect(content().string(containsString("Avaliações")))
                .andExpect(content().string(not(containsString("evento.bannerUrl"))));
    }

    @Test
    void devePreservarBannerExternoDoCloudinaryNoHero() throws Exception {
        String banner = "https://res.cloudinary.com/demo/image/upload/v1/banner.jpg";
        Evento evento = eventoPublicado(2L);
        evento.setBannerUrl(banner);
        when(eventoService.buscarPorId(EVENTO_ID)).thenReturn(evento);
        when(avaliacaoEventoService.obterResumo(EVENTO_ID)).thenReturn(resumoSemAvaliacoes());
        when(avaliacaoEventoService.listarDoEvento(eq(EVENTO_ID), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/eventos/{id}", EVENTO_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("url(&#39;" + banner + "&#39;)")));
    }

    @Test
    void deveManterTratamentoAtualParaEventoInexistente() throws Exception {
        when(eventoService.buscarPorId(EVENTO_ID))
                .thenThrow(new sistema.eventos.exception.NegocioException("Evento não encontrado"));

        mockMvc.perform(get("/eventos/{id}", EVENTO_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Erro 400 - Requisição Inválida")));
    }

    private void autenticar(Long id, String nome) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome(nome);
        when(usuarioService.buscarPorEmail(EMAIL_PARTICIPANTE)).thenReturn(usuario);

        UserDetails userDetails = User.withUsername(EMAIL_PARTICIPANTE)
                .password("senha")
                .roles("PARTICIPANTE")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    private Evento eventoPublicado(Long organizadorId) {
        Usuario organizador = new Usuario();
        organizador.setId(organizadorId);
        organizador.setNome("Organizador");

        Evento evento = new Evento();
        evento.setId(EVENTO_ID);
        evento.setTitulo("Curso de Teste");
        evento.setDescricao("Descrição do curso de teste.");
        evento.setStatus(StatusEvento.PUBLICADO);
        evento.setOrganizador(organizador);
        evento.setVagasTotais(50);
        evento.setVagasRestantes(50);
        evento.setCargaHoraria(20);
        evento.setLocal("Auditório SEPLAG");
        return evento;
    }

    private AvaliacaoResumoDto resumoComAvaliacoes(double media, long quantidade) {
        return AvaliacaoResumoDto.builder()
                .media(media)
                .quantidade(quantidade)
                .temAvaliacoes(true)
                .mediaFormatada(String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", media))
                .percentualEstrelas((media / 5.0) * 100.0)
                .build();
    }

    private AvaliacaoResumoDto resumoSemAvaliacoes() {
        return AvaliacaoResumoDto.builder()
                .media(0.0)
                .quantidade(0L)
                .temAvaliacoes(false)
                .mediaFormatada(null)
                .percentualEstrelas(0.0)
                .build();
    }

    private Page<AvaliacaoEventoResponseDto> paginaDeAvaliacoes() {
        AvaliacaoEventoResponseDto avaliacao = AvaliacaoEventoResponseDto.builder()
                .id(1L)
                .nomeUsuario("Maria Silva")
                .nota(5)
                .comentario("Excelente curso.")
                .dataAvaliacao(LocalDateTime.of(2026, 10, 27, 10, 0))
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        return new PageImpl<>(List.of(avaliacao), pageable, 1);
    }

    private AvaliacaoEventoResponseDto avaliacaoDoUsuario() {
        return AvaliacaoEventoResponseDto.builder()
                .id(2L)
                .nomeUsuario("Maria Silva")
                .nota(4)
                .comentario("Bom curso.")
                .dataAvaliacao(LocalDateTime.of(2026, 10, 27, 11, 0))
                .build();
    }
}
