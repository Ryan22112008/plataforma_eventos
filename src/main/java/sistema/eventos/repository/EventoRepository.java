package sistema.eventos.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sistema.eventos.dto.UsuarioEventoTituloProjection;
import sistema.eventos.model.Evento;
import sistema.eventos.model.ModalidadeEvento;
import sistema.eventos.model.NichoEvento;
import sistema.eventos.model.StatusEvento;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventoRepository extends JpaRepository<Evento, Long> {

     List<Evento> findByOrganizadorId(Long organizadorId);

    @Query("SELECT e.organizador.id, COUNT(e) FROM Evento e " +
            "WHERE e.organizador.id IN :organizadorIds GROUP BY e.organizador.id")
    List<Object[]> contarEventosPorOrganizadores(@Param("organizadorIds") List<Long> organizadorIds);

    /**
     * Carrega em lote os títulos dos eventos organizados pelos usuários
     * informados, usando o relacionamento real Evento.organizador.
     * Usado pela exportação do Super Admin sem consultas por usuário.
     */
    @Query("SELECT e.organizador.id AS usuarioId, e.titulo AS titulo " +
            "FROM Evento e " +
            "WHERE e.organizador.id IN :usuarioIds " +
            "ORDER BY e.organizador.id, e.titulo")
    List<UsuarioEventoTituloProjection> findTitulosEventosOrganizadosPorUsuarios(
            @Param("usuarioIds") List<Long> usuarioIds);

     List<Evento> findByTituloContainingIgnoreCase(String titulo);

     @Query("SELECT e FROM Evento e JOIN FETCH e.organizador WHERE e.id = :id")
     Optional<Evento> findByIdComOrganizador(@Param("id") Long id);

     // --- Busca apenas eventos ativos e com vagas para a listagem principal ---
     @Query("SELECT e FROM Evento e WHERE e.status = :status AND e.vagasRestantes > 0 ORDER BY e.dataInicio ASC")
     List<Evento> buscarEventosDisponiveis(@Param("status") StatusEvento status);

     @Query(value = "SELECT e FROM Evento e WHERE e.status = :status " +
             "AND (:temBusca = true OR e.vagasRestantes > 0) " +
             "AND (:temBusca = false OR LOWER(e.titulo) LIKE LOWER(CONCAT('%', :busca, '%')) " +
             "OR CAST(e.descricao AS string) LIKE CONCAT('%', :busca, '%')) " +
             "AND (:nicho IS NULL OR e.nicho = :nicho) " +
             "AND (:modalidade IS NULL OR e.modalidade = :modalidade) " +
             "AND (:dataInicio IS NULL OR e.dataInicio = :dataInicio) " +
             "AND (:vigencia IS NULL " +
             "OR (:vigencia = 'FUTUROS' AND e.dataInicio > :hoje) " +
             "OR (:vigencia = 'EM_ANDAMENTO' AND e.dataInicio <= :hoje AND e.dataTermino >= :hoje) " +
             "OR (:vigencia = 'ENCERRADOS' AND e.dataTermino < :hoje))",
             countQuery = "SELECT COUNT(e) FROM Evento e WHERE e.status = :status " +
                     "AND (:temBusca = true OR e.vagasRestantes > 0) " +
                     "AND (:temBusca = false OR LOWER(e.titulo) LIKE LOWER(CONCAT('%', :busca, '%')) " +
                     "OR CAST(e.descricao AS string) LIKE CONCAT('%', :busca, '%')) " +
                     "AND (:nicho IS NULL OR e.nicho = :nicho) " +
                     "AND (:modalidade IS NULL OR e.modalidade = :modalidade) " +
                     "AND (:dataInicio IS NULL OR e.dataInicio = :dataInicio) " +
                     "AND (:vigencia IS NULL " +
                     "OR (:vigencia = 'FUTUROS' AND e.dataInicio > :hoje) " +
                     "OR (:vigencia = 'EM_ANDAMENTO' AND e.dataInicio <= :hoje AND e.dataTermino >= :hoje) " +
                     "OR (:vigencia = 'ENCERRADOS' AND e.dataTermino < :hoje))")
     Page<Evento> buscarEventosPublicos(@Param("status") StatusEvento status,
                                        @Param("temBusca") boolean temBusca,
                                        @Param("busca") String busca,
                                        @Param("nicho") NichoEvento nicho,
                                        @Param("modalidade") ModalidadeEvento modalidade,
                                        @Param("dataInicio") LocalDate dataInicio,
                                        @Param("vigencia") String vigencia,
                                        @Param("hoje") LocalDate hoje,
                                        Pageable pageable);

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
