package sistema.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sistema.eventos.model.SorteioResultado;

import java.util.List;

public interface SorteioResultadoRepository extends JpaRepository<SorteioResultado, Long> {
    List<SorteioResultado> findByEventoIdOrderByRealizadoEmDescIdDesc(Long eventoId);
}
