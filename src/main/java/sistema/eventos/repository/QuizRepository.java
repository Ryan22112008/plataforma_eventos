package sistema.eventos.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema.eventos.model.Quiz;
import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz,Long> {
    List<Quiz> findByOrganizadorIdOrderByIdDesc(Long id);
    @EntityGraph(attributePaths={"evento","perguntas","perguntas.alternativas"}) Optional<Quiz> findWithQuestionsById(Long id);
    @EntityGraph(attributePaths={"evento","organizador","perguntas","perguntas.alternativas"}) Optional<Quiz> findFullById(Long id);
    @EntityGraph(attributePaths={"evento","organizador","perguntas","perguntas.alternativas"}) Optional<Quiz> findByCodigoIgnoreCase(String codigo);
}
