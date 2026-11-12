package sistema.eventos.model;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="tb_quiz_resposta",uniqueConstraints=@UniqueConstraint(columnNames={"jogador_id","pergunta_id"})) @Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class QuizResposta {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="jogador_id",nullable=false) private QuizJogador jogador;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="pergunta_id",nullable=false) private QuizPergunta pergunta;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="alternativa_id") private QuizAlternativa alternativa;
    @Column(nullable=false) private boolean correta;
    @Column(nullable=false) private Integer pontos;
    @Column(nullable=false) private Long tempoMs;
}
