package sistema.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import sistema.eventos.model.Evento;
import sistema.eventos.model.StatusTrabalho;
import sistema.eventos.model.TrabalhoCientifico;

import java.util.List;

@Repository
public interface TrabalhoCientificoRepository extends JpaRepository<TrabalhoCientifico, Long> {

    // --- CORRIGIDO: Adicionado JOIN FETCH para otimizar o carregamento na tela "Minhas Submissões" do Usuário ---
    @Query("SELECT t FROM TrabalhoCientifico t JOIN FETCH t.evento WHERE t.autor.id = :autorId")
    List<TrabalhoCientifico> findByAutorIdComEvento(@Param("autorId") Long autorId);

    @Query("SELECT t FROM TrabalhoCientifico t JOIN FETCH t.autor WHERE t.evento.id = :eventoId")
    List<TrabalhoCientifico> findByEventoIdComAutor(@Param("eventoId") Long eventoId);

    // --- NOVO: Traz os dados completos filtrados por status, essencial para o fluxo de avaliação da banca acadêmica ---
    @Query("SELECT t FROM TrabalhoCientifico t " +
            "JOIN FETCH t.autor " +
            "JOIN FETCH t.evento " +
            "WHERE t.evento.id = :eventoId AND t.status = :status")
    List<TrabalhoCientifico> findByEventoIdEStatusComRelacoes(@Param("eventoId") Long eventoId, @Param("status") StatusTrabalho status);

    List<TrabalhoCientifico> findByEvento(Evento evento);
}