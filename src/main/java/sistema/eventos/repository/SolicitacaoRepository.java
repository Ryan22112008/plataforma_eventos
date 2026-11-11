package sistema.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sistema.eventos.model.SolicitacaoOrganizador;
import sistema.eventos.model.StatusSolicitacao;

import java.util.List;
import java.util.Optional;

@Repository
public interface SolicitacaoRepository extends JpaRepository<SolicitacaoOrganizador, Long> {

    /**
     * Atualiza somente o status da solicitação.
     *
     * <p>Alterações de status em registros legados não podem passar pelo update
     * completo da entidade: campos antigos com NULL/vazio são validados por
     * Bean Validation e também seriam reenviados ao banco, causando falha em
     * colunas NOT NULL. Esta atualização direcionada preserva os demais valores
     * exatamente como estão.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SolicitacaoOrganizador s set s.status = :novoStatus "
            + "where s.id = :id and s.status = :statusAtual")
    int atualizarStatus(@Param("id") Long id,
                        @Param("novoStatus") StatusSolicitacao novoStatus,
                        @Param("statusAtual") StatusSolicitacao statusAtual);

    // 🎯 1. Para listar na tela do Admin apenas as solicitações que estão aguardando aprovação
    List<SolicitacaoOrganizador> findByStatus(StatusSolicitacao status);

    // 🔒 2. Blindagem de segurança: Evita que o mesmo usuário clique várias vezes
    // e crie um monte de solicitações duplicadas enquanto estiver PENDENTE
    boolean existsByUsuarioIdAndStatus(Long usuarioId, StatusSolicitacao status);

    Optional<SolicitacaoOrganizador> findByUsuarioId(Long usuarioId);

    boolean existsByUsuario_EmailIgnoreCaseAndStatus(String email, StatusSolicitacao status);
}
