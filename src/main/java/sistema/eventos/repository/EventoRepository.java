package sistema.eventos.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sistema.eventos.model.Evento;
import sistema.eventos.model.StatusEvento;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventoRepository extends JpaRepository<Evento, Long> {

     List<Evento> findByOrganizadorId(Long organizadorId);

     List<Evento> findByTituloContainingIgnoreCase(String titulo);

     @Query("SELECT e FROM Evento e JOIN FETCH e.organizador WHERE e.id = :id")
     Optional<Evento> findByIdComOrganizador(@Param("id") Long id);

     // --- Busca apenas eventos ativos e com vagas para a listagem principal ---
     @Query("SELECT e FROM Evento e WHERE e.status = :status AND e.vagasRestantes > 0 ORDER BY e.dataInicio ASC")
     List<Evento> buscarEventosDisponiveis(@Param("status") StatusEvento status);

     // --- Lock Pessimista para evitar overbooking em inscrições simultâneas ---
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("SELECT e FROM Evento e WHERE e.id = :id")
     Optional<Evento> findByIdForUpdate(@Param("id") Long id);

     // --- CORRIGIDO: Query adaptada com CAST para evitar erro de LOWER em campos TEXT/CLOB ---
     @Query("SELECT e FROM Evento e WHERE " +
             "(LOWER(e.titulo) LIKE LOWER(CONCAT('%', :termo, '%')) OR " +
             "CAST(e.descricao AS string) LIKE CONCAT('%', :termo, '%')) " +
             "AND e.status = :status")
     List<Evento> pesquisarEventosAtivos(@Param("termo") String termo, @Param("status") StatusEvento status);
}