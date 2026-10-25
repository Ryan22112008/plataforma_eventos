package sistema.eventos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import sistema.eventos.controller.SuperAdminSolicitacaoController;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.SolicitacaoOrganizador;
import sistema.eventos.model.StatusSolicitacao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.SolicitacaoRepository;
import sistema.eventos.repository.UsuarioRepository;
import sistema.eventos.service.EmailService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void aprovarSolicitacaoPendenteConcedePerfilDeOrganizador() {
        SolicitacaoOrganizador solicitacao = solicitacaoPendenteCompleta();
        Usuario usuario = solicitacao.getUsuario();
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacao));

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();
        String view = controller.aprovarProfessor(1L, attributes);

        assertEquals("redirect:/super-admin/solicitacoes", view);
        assertEquals(StatusSolicitacao.APROVADO, solicitacao.getStatus());
        assertEquals(Perfil.ROLE_ORGANIZADOR, usuario.getPerfil());
        verify(solicitacaoRepository).save(solicitacao);
        verify(usuarioRepository).save(usuario);
        verify(emailService).enviarSolicitacaoAprovada(usuario);
    }

    @Test
    void rejeitarSolicitacaoPendenteMantemPerfilDeParticipante() {
        SolicitacaoOrganizador solicitacao = solicitacaoPendenteCompleta();
        Usuario usuario = solicitacao.getUsuario();
        when(solicitacaoRepository.findById(1L)).thenReturn(Optional.of(solicitacao));

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();
        String view = controller.rejeitarSolicitacao(1L, attributes);

        assertEquals("redirect:/super-admin/solicitacoes", view);
        assertEquals(StatusSolicitacao.REJEITADO, solicitacao.getStatus());
        assertEquals(Perfil.ROLE_PARTICIPANTE, usuario.getPerfil());
        verify(solicitacaoRepository).save(solicitacao);
        verify(emailService).enviarSolicitacaoRejeitada(usuario);
        verifyNoInteractions(usuarioRepository);
    }

    private static SolicitacaoOrganizador solicitacao() {
        SolicitacaoOrganizador solicitacao = new SolicitacaoOrganizador();
        solicitacao.setId(1L);
        return solicitacao;
    }

    private static SolicitacaoOrganizador solicitacaoPendenteCompleta() {
        SolicitacaoOrganizador solicitacao = solicitacao();
        solicitacao.setNomeCompleto("Maria Silva");
        solicitacao.setMatricula("123456");
        solicitacao.setEmailInstitucional("maria@seplag.mt.gov.br");
        solicitacao.setJustificativa("Atuo na organização de cursos institucionais.");
        solicitacao.setOrgao("SEPLAG-MT");
        solicitacao.setStatus(StatusSolicitacao.PENDENTE);

        Usuario usuario = new Usuario();
        usuario.setId(10L);
        usuario.setPerfil(Perfil.ROLE_PARTICIPANTE);
        usuario.setEmail("maria@seplag.mt.gov.br");
        solicitacao.setUsuario(usuario);
        return solicitacao;
    }
}
