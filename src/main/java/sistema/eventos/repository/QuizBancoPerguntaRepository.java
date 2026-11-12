package sistema.eventos.repository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema.eventos.model.QuizBancoPergunta;
import java.util.List;
import java.util.Optional;
public interface QuizBancoPerguntaRepository extends JpaRepository<QuizBancoPergunta,Long> {
 @EntityGraph(attributePaths={"alternativas"}) List<QuizBancoPergunta> findByOrganizadorIdOrderByIdDesc(Long id);
 @EntityGraph(attributePaths={"alternativas","organizador"}) Optional<QuizBancoPergunta> findWithOwnerById(Long id);
}
