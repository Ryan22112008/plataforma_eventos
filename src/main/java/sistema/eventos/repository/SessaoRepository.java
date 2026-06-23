package sistema.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sistema.eventos.model.Sessao;
import java.util.List;

@Repository
public interface SessaoRepository extends JpaRepository<Sessao, Long> {

    // 🎯 CORRIGIDO: Agora busca SESSÃO de verdade na tabela correta
    @Query("SELECT s FROM Sessao s WHERE s.evento.id = :eventoId")
    List<Sessao> findByEventoId(@Param("eventoId") Long eventoId);

    List<Sessao> findByEventoIdOrderByDataHoraInicioAsc(Long eventoId);

    long countByEventoId(Long eventoId);

    void deleteByEventoId(Long eventoId);

    java.util.Optional<Sessao> findByQrCodeToken(String qrCodeToken);
}