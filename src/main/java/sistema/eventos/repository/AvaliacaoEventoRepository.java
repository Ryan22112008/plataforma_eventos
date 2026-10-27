package sistema.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sistema.eventos.model.AvaliacaoEvento;

import java.util.List;
import java.util.Optional;

@Repository
public interface AvaliacaoEventoRepository extends JpaRepository<AvaliacaoEvento, Long> {

    List<AvaliacaoEvento> findByEventoIdOrderByDataCriacaoDesc(Long eventoId);

    Optional<AvaliacaoEvento> findByEventoIdAndUsuarioId(Long eventoId, Long usuarioId);

    boolean existsByEventoIdAndUsuarioId(Long eventoId, Long usuarioId);

    // M\u00e9dia calculada diretamente no banco, evitando carregar todas as avalia\u00e7\u00f5es
    @Query("SELECT AVG(a.nota) FROM AvaliacaoEvento a WHERE a.evento.id = :eventoId")
    Double mediaNotasPorEvento(@Param("eventoId") Long eventoId);

    @Query("SELECT COUNT(a) FROM AvaliacaoEvento a WHERE a.evento.id = :eventoId")
    Long contarPorEvento(@Param("eventoId") Long eventoId);
}
