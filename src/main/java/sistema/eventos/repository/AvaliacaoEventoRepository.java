package sistema.eventos.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sistema.eventos.model.AvaliacaoEvento;

import java.util.Optional;

@Repository
public interface AvaliacaoEventoRepository extends JpaRepository<AvaliacaoEvento, Long> {

    @EntityGraph(attributePaths = "usuario")
    Page<AvaliacaoEvento> findByEventoId(Long eventoId, Pageable pageable);

    Optional<AvaliacaoEvento> findByEventoIdAndUsuarioId(Long eventoId, Long usuarioId);

    boolean existsByEventoIdAndUsuarioId(Long eventoId, Long usuarioId);

    @Query("SELECT AVG(a.nota) FROM AvaliacaoEvento a WHERE a.evento.id = :eventoId")
    Double mediaNotasPorEvento(@Param("eventoId") Long eventoId);

    @Query("SELECT COUNT(a) FROM AvaliacaoEvento a WHERE a.evento.id = :eventoId")
    long contarPorEvento(@Param("eventoId") Long eventoId);
}
