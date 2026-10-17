package sistema.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sistema.eventos.model.SolicitacaoOrganizador;
import sistema.eventos.model.StatusSolicitacao;

import java.util.List;
import java.util.Optional;

@Repository
public interface SolicitacaoRepository extends JpaRepository<SolicitacaoOrganizador, Long> {

    // 🎯 1. Para listar na tela do Admin apenas as solicitações que estão aguardando aprovação
    List<SolicitacaoOrganizador> findByStatus(StatusSolicitacao status);

    // 🔒 2. Blindagem de segurança: Evita que o mesmo usuário clique várias vezes
    // e crie um monte de solicitações duplicadas enquanto estiver PENDENTE
    boolean existsByUsuarioIdAndStatus(Long usuarioId, StatusSolicitacao status);

    Optional<SolicitacaoOrganizador> findByUsuarioId(Long usuarioId);

    boolean existsByUsuario_EmailIgnoreCaseAndStatus(String email, StatusSolicitacao status);
}
