package sistema.eventos.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema.eventos.model.QuizJogador;
import java.util.Optional;
public interface QuizJogadorRepository extends JpaRepository<QuizJogador,Long> {
 Optional<QuizJogador> findByQuizIdAndUsuarioId(Long quizId,Long usuarioId);
}
