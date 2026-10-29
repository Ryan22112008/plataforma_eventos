package sistema.eventos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import sistema.eventos.controller.ParticipanteController;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.service.CertificadoService;
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.PresencaService;
import sistema.eventos.service.UsuarioService;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ParticipanteController.class)
@AutoConfigureMockMvc(addFilters = false)
class MeusCertificadosControllerWebTest {

    private static final String EMAIL_PARTICIPANTE = "participante@seplag.mt.gov.br";
    private static final Long PARTICIPANTE_ID = 1L;
    private static final Long INSCRICAO_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InscricaoService inscricaoService;

    @MockBean
    private UsuarioService usuarioService;

    @MockBean
    private CertificadoService certificadoService;

    @MockBean
    private PresencaService presencaService;

    @MockBean
    private SessaoRepository sessaoRepository;

    @AfterEach
    void limparContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveRenderizarCertificadoComDadosReaisEDownloadExistente() throws Exception {
        autenticar();
        when(inscricaoService.buscarCertificadosDoUsuario(PARTICIPANTE_ID))
                .thenReturn(List.of(certificado(INSCRICAO_ID, "Tecnologia de IA")));

        mockMvc.perform(get("/participante/meus-certificados"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("Área do participante")))
                .andExpect(content().string(containsString("Meus certificados")))
                .andExpect(content().string(containsString("Certificados liberados para a sua participação")))
                .andExpect(content().string(containsString("Seus certificados")))
                .andExpect(content().string(containsString(">1</strong>")))
                .andExpect(content().string(containsString("certificado disponível")))
                .andExpect(content().string(containsString("Presença confirmada")))
                .andExpect(content().string(containsString("Tecnologia de IA")))
                .andExpect(content().string(containsString("Certificado de participação")))
                .andExpect(content().string(containsString("Carga horária")))
                .andExpect(content().string(containsString("<span>20</span> horas")))
                .andExpect(content().string(containsString("01/10/2026 a 05/10/2026")))
                .andExpect(content().string(containsString("Baixar certificado")))
                .andExpect(content().string(containsString(
                        "href=\"/participante/inscricoes/" + INSCRICAO_ID + "/certificado\"")))
                .andExpect(content().string(containsString("target=\"_blank\"")))
                .andExpect(content().string(containsString("rel=\"noopener\"")));
    }

    @Test
    void deveRenderizarEstadoVazioInstitucionalComRotaExistente() throws Exception {
        autenticar();
        when(inscricaoService.buscarCertificadosDoUsuario(PARTICIPANTE_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/participante/meus-certificados"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhum certificado disponível")))
                .andExpect(content().string(containsString(
                        "Os certificados aparecem aqui depois que sua presença for confirmada pela organização do evento.")))
                .andExpect(content().string(containsString("Explorar cursos")))
                .andExpect(content().string(containsString("href=\"/\"")))
                .andExpect(content().string(not(containsString("seplag-cert-card"))));
    }

    @Test
    void deveRenderizarMultiplosCertificadosSemLayoutDeCertificadoUnico() throws Exception {
        autenticar();
        when(inscricaoService.buscarCertificadosDoUsuario(PARTICIPANTE_ID))
                .thenReturn(List.of(
                        certificado(INSCRICAO_ID, "Tecnologia de IA"),
                        certificado(11L, "Gestão Pública na Prática")));

        mockMvc.perform(get("/participante/meus-certificados"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">2</strong>")))
                .andExpect(content().string(containsString("certificados disponíveis")))
                .andExpect(content().string(containsString("Tecnologia de IA")))
                .andExpect(content().string(containsString("Gestão Pública na Prática")))
                .andExpect(content().string(containsString(
                        "href=\"/participante/inscricoes/" + INSCRICAO_ID + "/certificado\"")))
                .andExpect(content().string(containsString(
                        "href=\"/participante/inscricoes/11/certificado\"")))
                .andExpect(content().string(not(containsString("seplag-cert-grid--single"))));
    }

    private void autenticar() {
        Usuario participante = new Usuario();
        participante.setId(PARTICIPANTE_ID);
        participante.setNome("Participante");
        participante.setEmail(EMAIL_PARTICIPANTE);
        when(usuarioService.buscarPorEmail(EMAIL_PARTICIPANTE)).thenReturn(participante);

        UserDetails userDetails = User.withUsername(EMAIL_PARTICIPANTE)
                .password("senha")
                .roles("PARTICIPANTE")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    private Inscricao certificado(Long id, String titulo) {
        Evento evento = new Evento();
        evento.setId(100L + id);
        evento.setTitulo(titulo);
        evento.setCargaHoraria(20);
        evento.setDataInicio(LocalDate.of(2026, 10, 1));
        evento.setDataTermino(LocalDate.of(2026, 10, 5));

        return Inscricao.builder()
                .id(id)
                .evento(evento)
                .status(StatusInscricao.UTILIZADO)
                .build();
    }
}
