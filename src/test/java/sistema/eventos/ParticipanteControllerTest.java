package sistema.eventos;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import sistema.eventos.controller.ParticipanteController;
import sistema.eventos.dto.RequestSolicitacaoOrganizadorDto;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.service.CertificadoService;
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.PresencaService;
import sistema.eventos.service.UsuarioService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ParticipanteControllerTest {

    private static final SpringValidatorAdapter VALIDATOR =
            new SpringValidatorAdapter(Validation.buildDefaultValidatorFactory().getValidator());

    @Mock
    private InscricaoService inscricaoService;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private CertificadoService certificadoService;

    @Mock
    private PresencaService presencaService;

    @Mock
    private SessaoRepository sessaoRepository;

    @InjectMocks
    private ParticipanteController controller;

    @Test
    void telaSolicitarOrganizadorDeveRetornarFormulario() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.telaSolicitarAcessoOrganizador(model);

        assertEquals("solicitar-organizador", view);
        assertTrue(model.getAttribute("solicitacaoDto") instanceof RequestSolicitacaoOrganizadorDto);
    }

    @Test
    void solicitacaoValidaComOrgaoDeveSerEnviada() {
        RequestSolicitacaoOrganizadorDto dto = dtoComOrgao();
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(dto, "solicitacaoDto");
        VALIDATOR.validate(dto, result);
        assertFalse(result.hasErrors());

        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.solicitarAcessoOrganizador(usuario(), dto, result, model, attributes);

        assertEquals("redirect:/eventos", view);
        verify(usuarioService).solicitarAcessoOrganizador("maria@seplag.mt.gov.br", dto);
        assertEquals(Boolean.TRUE, attributes.getFlashAttributes().get("solicitacaoOrganizadorEnviada"));
    }

    @Test
    void solicitacaoSemOrgaoDeveRetornarFormularioPreservandoDados() {
        RequestSolicitacaoOrganizadorDto dto = dtoComOrgao();
        dto.setOrgao(null);
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(dto, "solicitacaoDto");
        VALIDATOR.validate(dto, result);
        assertTrue(result.hasFieldErrors("orgao"));

        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.solicitarAcessoOrganizador(usuario(), dto, result, model, attributes);

        assertEquals("solicitar-organizador", view);
        assertSame(dto, model.getAttribute("solicitacaoDto"));
        assertEquals("Maria Silva", ((RequestSolicitacaoOrganizadorDto) model.getAttribute("solicitacaoDto")).getNomeCompleto());
        assertNotNull(model.getAttribute("erroSolicitacaoOrganizador"));
        verifyNoInteractions(usuarioService);
    }

    private static RequestSolicitacaoOrganizadorDto dtoComOrgao() {
        return RequestSolicitacaoOrganizadorDto.builder()
                .nomeCompleto("Maria Silva")
                .matricula("123456")
                .emailInstitucional("maria@seplag.mt.gov.br")
                .justificativa("Atuo na organização de eventos institucionais.")
                .orgao("SEPLAG-MT")
                .build();
    }

    private static UserDetails usuario() {
        return User.withUsername("maria@seplag.mt.gov.br")
                .password("senha")
                .authorities("ROLE_PARTICIPANTE")
                .build();
    }
}
