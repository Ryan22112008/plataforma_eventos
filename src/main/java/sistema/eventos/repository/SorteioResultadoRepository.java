package sistema.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sistema.eventos.model.SorteioResultado;

import java.util.List;

public interface SorteioResultadoRepository extends JpaRepository<SorteioResultado, Long> {

    List<SorteioResultado> findByEventoIdOrderByRealizadoEmDescIdDesc(Long eventoId);

    List<SorteioResultado> findByRodadaSorteioIdOrderByIdAsc(Long rodadaId);

    @Modifying
    @Query("UPDATE SorteioResultado r SET r.substituidoPor = null WHERE r.evento.id = :eventoId")
    int limparVinculosDeSubstituicao(@Param("eventoId") Long eventoId);

    @Modifying
    @Query("DELETE FROM SorteioResultado r WHERE r.evento.id = :eventoId")
    int excluirPorEvento(@Param("eventoId") Long eventoId);
}
