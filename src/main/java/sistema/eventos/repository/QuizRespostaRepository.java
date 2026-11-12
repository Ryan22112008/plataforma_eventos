package sistema.eventos.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema.eventos.model.QuizResposta;
import java.util.List;
public interface QuizRespostaRepository extends JpaRepository<QuizResposta,Long> {
 boolean existsByJogadorIdAndPerguntaId(Long jogadorId,Long perguntaId);
 List<QuizResposta> findByPerguntaId(Long perguntaId);
 List<QuizResposta> findByJogadorQuizId(Long quizId);
}
