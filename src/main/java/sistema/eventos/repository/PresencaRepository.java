package sistema.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sistema.eventos.dto.PresencaPorAlunoProjection;
import sistema.eventos.model.Presenca;
import sistema.eventos.model.StatusPresenca;

import java.util.List;
import java.util.Optional;

@Repository
public interface PresencaRepository extends JpaRepository<Presenca, Long> {

    @Query("SELECT p FROM Presenca p WHERE p.sessao.evento.id = :eventoId")
    List<Presenca> findByEventoId(@Param("eventoId") Long eventoId);

    // Agora sim: estes métodos estão na classe correta e o JPA saberá mapeá-los para a tabela TB_PRESENCA
    boolean existsByAlunoIdAndSessaoId(Long alunoId, Long sessaoId);

    @Query("SELECT p FROM Presenca p JOIN FETCH p.aluno WHERE p.sessao.id = :sessaoId")
    List<Presenca> findBySessaoIdComAluno(@Param("sessaoId") Long sessaoId);

    Optional<Presenca> findByAlunoIdAndSessaoId(Long alunoId, Long sessaoId);

    // No seu PresencaRepository.java
    @Query("SELECT COUNT(p) FROM Presenca p WHERE p.aluno.id = :alunoId AND p.sessao.evento.id = :eventoId AND p.status = 'PRESENTE'")
    long countPresencasConfirmadasPorAluno(@Param("alunoId") Long alunoId, @Param("eventoId") Long eventoId);

    /**
     * Conta, em uma única consulta, quantas sessões distintas do evento cada
     * aluno tem com presença PRESENTE. O join com a sessão garante que apenas
     * presenças vinculadas a sessões reais do evento entrem no cálculo.
     */
    @Query("SELECT p.aluno.id AS alunoId, COUNT(DISTINCT p.sessao.id) AS totalPresentes " +
            "FROM Presenca p JOIN p.sessao s " +
            "WHERE s.evento.id = :eventoId AND p.status = :status " +
            "GROUP BY p.aluno.id")
    List<PresencaPorAlunoProjection> contarPresencasPresentesPorAluno(
            @Param("eventoId") Long eventoId,
            @Param("status") StatusPresenca status);
}