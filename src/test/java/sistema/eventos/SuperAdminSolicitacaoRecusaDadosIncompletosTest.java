package sistema.eventos;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import sistema.eventos.controller.SuperAdminSolicitacaoController;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.SolicitacaoRepository;
import sistema.eventos.repository.UsuarioRepository;
import sistema.eventos.service.EmailService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Regressão do erro 500 ao recusar solicitações legadas.
 *
 * <p>Os registros antigos podem ter campos obrigatórios NULL no banco. O
 * update completo da entidade dispara Bean Validation e reenvia esses NULLs
 * para colunas NOT NULL. O teste reproduz esse cenário e garante que a recusa
 * altere apenas o status, sem inventar ou apagar dados.</p>
 */
@DataJpaTest
class SuperAdminSolicitacaoRecusaDadosIncompletosTest {

    @Autowired
    private SolicitacaoRepository solicitacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EntityManager entityManager;

    private EmailService emailService;

    @BeforeEach
    void preparar() {
        emailService = mock(EmailService.class);
    }

    @Test
    void deveRecusarSolicitacaoComCamposNulosSemFalharEPreservandoDados() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nome("Solicitante Legado")
                .email("legado@seplag.mt.gov.br")
                .senha("senha-segura")
                .perfil(Perfil.ROLE_PARTICIPANTE)
                .ativo(true)
                .build());

        permitirCamposLegadosNulos();
        entityManager.createNativeQuery("""
                        insert into tb_solicitacao_organizador
                            (usuario_id, nome_completo, matricula, email_institucional,
                             justificativa, orgao, data_solicitacao, status)
                        values (:usuarioId, null, null, null, null, null, current_timestamp, 'PENDENTE')
                        """)
                .setParameter("usuarioId", usuario.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        Long solicitacaoId = ((Number) entityManager.createNativeQuery(
                        "select max(id) from tb_solicitacao_organizador")
                .getSingleResult()).longValue();

        SuperAdminSolicitacaoController controller = new SuperAdminSolicitacaoController(
                solicitacaoRepository, usuarioRepository, emailService);
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.rejeitarSolicitacao(solicitacaoId, attributes);

        assertEquals("redirect:/super-admin/solicitacoes", view);
        assertEquals("REJEITADO", statusDaSolicitacao(solicitacaoId));
        assertNull(campoDaSolicitacao(solicitacaoId, "nome_completo"));
        assertNull(campoDaSolicitacao(solicitacaoId, "matricula"));
        assertNull(campoDaSolicitacao(solicitacaoId, "email_institucional"));
        assertNull(campoDaSolicitacao(solicitacaoId, "justificativa"));
        verify(emailService).enviarSolicitacaoRejeitada(argThat(
                destinatario -> destinatario != null
                        && "legado@seplag.mt.gov.br".equals(destinatario.getEmail())));
    }

    private void permitirCamposLegadosNulos() {
        entityManager.createNativeQuery(
                "alter table tb_solicitacao_organizador alter column nome_completo drop not null")
                .executeUpdate();
        entityManager.createNativeQuery(
                "alter table tb_solicitacao_organizador alter column matricula drop not null")
                .executeUpdate();
        entityManager.createNativeQuery(
                "alter table tb_solicitacao_organizador alter column email_institucional drop not null")
                .executeUpdate();
        entityManager.createNativeQuery(
                "alter table tb_solicitacao_organizador alter column justificativa drop not null")
                .executeUpdate();
    }

    private String statusDaSolicitacao(Long id) {
        return (String) entityManager.createNativeQuery(
                        "select status from tb_solicitacao_organizador where id = :id")
                .setParameter("id", id)
                .getSingleResult();
    }

    private Object campoDaSolicitacao(Long id, String coluna) {
        return entityManager.createNativeQuery(
                        "select " + coluna + " from tb_solicitacao_organizador where id = :id")
                .setParameter("id", id)
                .getSingleResult();
    }
}
