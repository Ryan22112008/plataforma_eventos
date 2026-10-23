package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import sistema.eventos.controller.SuperAdminSolicitacaoController;
import sistema.eventos.model.SolicitacaoOrganizador;
import sistema.eventos.repository.SolicitacaoRepository;
import sistema.eventos.repository.UsuarioRepository;
import sistema.eventos.service.EmailService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminSolicitacaoControllerTest {

    @Mock
    private SolicitacaoRepository solicitacaoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private SuperAdminSolicitacaoController controller;

    @Test
    void edicaoDeSolicitacaoDevePersistirOrgao() {
        SolicitacaoOrganizador solicitacao = solicitacao();
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacao));

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();
        String view = controller.salvarEdicaoSolicitacao(
                1L,
                "Maria Silva",
                "123456",
                "maria@seplag.mt.gov.br",
                "Atuo na organização de eventos institucionais.",
                "  SEPLAG-MT  ",
                attributes);

        assertEquals("redirect:/super-admin/solicitacoes", view);
        assertEquals("SEPLAG-MT", solicitacao.getOrgao());
        verify(solicitacaoRepository).save(solicitacao);
    }

    @Test
    void edicaoComOrgaoNuloNaoDeveLancarExcecao() {
        SolicitacaoOrganizador solicitacao = solicitacao();
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacao));

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();
        String view = controller.salvarEdicaoSolicitacao(
                1L,
                "Maria Silva",
                "123456",
                "maria@seplag.mt.gov.br",
                "Atuo na organização de eventos institucionais.",
                null,
                attributes);

        assertEquals("redirect:/super-admin/solicitacoes", view);
        assertNull(solicitacao.getOrgao());
        verify(solicitacaoRepository).save(solicitacao);
    }

    private static SolicitacaoOrganizador solicitacao() {
        SolicitacaoOrganizador solicitacao = new SolicitacaoOrganizador();
        solicitacao.setId(1L);
        return solicitacao;
    }
}
