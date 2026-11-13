package sistema.eventos.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sistema.eventos.model.QuizResposta;
import java.util.List;
public interface QuizRespostaRepository extends JpaRepository<QuizResposta,Long> {
 boolean existsByJogadorIdAndPerguntaId(Long jogadorId,Long perguntaId);
 java.util.Optional<sistema.eventos.model.QuizResposta> findByJogadorIdAndPerguntaId(Long jogadorId,Long perguntaId);
 List<QuizResposta> findByPerguntaId(Long perguntaId);
 List<QuizResposta> findByJogadorQuizId(Long quizId);
 @Modifying
 @Query("delete from QuizResposta r where r.jogador.id in (select j.id from QuizJogador j where j.quiz.id = :quizId)")
 void deleteByQuizId(@Param("quizId") Long quizId);
}
