package sistema.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sistema.eventos.model.SorteioAuditoria;

import java.util.List;

public interface SorteioAuditoriaRepository extends JpaRepository<SorteioAuditoria, Long> {

    List<SorteioAuditoria> findTop200ByEventoIdOrderByRealizadoEmDescIdDesc(Long eventoId);
}
