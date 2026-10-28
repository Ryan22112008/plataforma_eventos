package sistema.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sistema.eventos.dto.UsuarioEventoTituloProjection;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.Presenca;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.Usuario;

import java.util.List;
import java.util.Optional;

@Repository
public interface InscricaoRepository extends JpaRepository<Inscricao, Long> {

    @Query("SELECT i FROM Inscricao i JOIN FETCH i.evento WHERE i.participante.id = :participanteId")
    List<Inscricao> findByParticipanteIdComEvento(@Param("participanteId") Long participanteId);

    // --- CORRIGIDO: Adicionado JOIN FETCH para trazer o Participante e evitar LazyInitializationException no painel ---
    @Query("SELECT i FROM Inscricao i JOIN FETCH i.participante WHERE i.evento.id = :eventoId")
    List<Inscricao> findByEventoId(@Param("eventoId") Long eventoId);

    @Query("SELECT i FROM Inscricao i LEFT JOIN FETCH i.participante LEFT JOIN FETCH i.evento WHERE i.id IN :ids")
    List<Inscricao> findAllByIdComRelacoes(@Param("ids") List<Long> ids);

    boolean existsByParticipanteIdAndEventoId(Long participanteId, Long eventoId);

    boolean existsByParticipanteIdAndEventoIdAndStatusNot(Long participanteId, Long eventoId, StatusInscricao status);

    // 🎯 ADICIONADO PARA O CHECK-IN VIA QR CODE:
    // Busca a inscrição única do aluno no evento e já traz o Evento junto (evitando Lazy)
    @Query("SELECT i FROM Inscricao i JOIN FETCH i.evento WHERE i.participante.id = :participanteId AND i.evento.id = :eventoId")
    Optional<Inscricao> findByParticipanteIdAndEventoIdComEvento(@Param("participanteId") Long participanteId, @Param("eventoId") Long eventoId);

    @Query("SELECT i FROM Inscricao i " +
            "JOIN FETCH i.participante " +
            "JOIN FETCH i.evento " +
            "WHERE i.tokenIngresso = :tokenIngresso")
    Optional<Inscricao> findByTokenIngressoComRelacoes(@Param("tokenIngresso") String tokenIngresso);

    @Query("SELECT COUNT(i) FROM Inscricao i WHERE i.evento.id = :eventoId AND i.status <> 'CANCELADO'")
    long contarInscricoesValidasDoEvento(@Param("eventoId") Long eventoId);

    // --- CORRIGIDO: Adicionado JOIN FETCH para trazer o Evento associado ao usuário na listagem de sucesso ---
    @Query("SELECT i FROM Inscricao i JOIN FETCH i.evento WHERE i.participante = :participante")
    List<Inscricao> findByParticipante(@Param("participante") Usuario participante);

    @Query("SELECT i FROM Inscricao i JOIN FETCH i.evento WHERE i.participante.id = :participanteId AND i.status = :status")
    List<Inscricao> findByParticipanteIdAndStatus(@Param("participanteId") Long participanteId, @Param("status") StatusInscricao status);

    /**
     * Carrega em lote os títulos dos eventos em que os usuários informados têm
     * inscrição com o status recebido. Usado pela exportação do Super Admin
     * para montar a coluna "Eventos Participados" sem N+1.
     * A regra de participação continua sendo a já existente no sistema
     * (status UTILIZADO), apenas parametrizada.
     */
    @Query("SELECT i.participante.id AS usuarioId, i.evento.titulo AS titulo " +
            "FROM Inscricao i " +
            "WHERE i.participante.id IN :usuarioIds AND i.status = :status " +
            "ORDER BY i.participante.id, i.evento.titulo")
    List<UsuarioEventoTituloProjection> findTitulosEventosPorParticipantes(
            @Param("usuarioIds") List<Long> usuarioIds,
            @Param("status") StatusInscricao status);

}
