package sistema.eventos.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema.eventos.model.Quiz;
import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz,Long> {
    List<Quiz> findByOrganizadorIdOrderByIdDesc(Long id);
    boolean existsByCodigoIgnoreCase(String codigo);
    // Do not fetch Quiz.perguntas and QuizPergunta.alternativas in the same
    // EntityGraph: both are List/bag collections and Hibernate rejects that fetch.
    @EntityGraph(attributePaths={"evento"}) Optional<Quiz> findWithQuestionsById(Long id);
    @EntityGraph(attributePaths={"evento","organizador"}) Optional<Quiz> findFullById(Long id);
    @EntityGraph(attributePaths={"evento","organizador"}) Optional<Quiz> findByCodigoIgnoreCase(String codigo);
}
