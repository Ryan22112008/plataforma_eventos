package sistema.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
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

    boolean existsByParticipanteIdAndEventoId(Long participanteId, Long eventoId);

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

}