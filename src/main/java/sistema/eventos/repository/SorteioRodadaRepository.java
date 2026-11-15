package sistema.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sistema.eventos.model.SorteioRodada;
import sistema.eventos.model.StatusRodadaSorteio;

import java.util.List;
import java.util.Optional;

public interface SorteioRodadaRepository extends JpaRepository<SorteioRodada, Long> {

    List<SorteioRodada> findByEventoIdOrderByNumeroRodadaDesc(Long eventoId);

    Optional<SorteioRodada> findFirstByEventoIdAndStatusOrderByNumeroRodadaDesc(
            Long eventoId, StatusRodadaSorteio status);

    Optional<SorteioRodada> findFirstByEventoIdOrderByNumeroRodadaDesc(Long eventoId);

    long countByEventoIdAndStatus(Long eventoId, StatusRodadaSorteio status);

    @Query("SELECT COALESCE(MAX(r.numeroRodada), 0) FROM SorteioRodada r WHERE r.evento.id = :eventoId")
    Integer maiorNumeroRodada(@Param("eventoId") Long eventoId);
}
