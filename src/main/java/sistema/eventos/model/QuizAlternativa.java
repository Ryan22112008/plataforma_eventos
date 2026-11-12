package sistema.eventos.model;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="tb_quiz_alternativa") @Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class QuizAlternativa {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="pergunta_id",nullable=false) private QuizPergunta pergunta;
    @Column(nullable=false,length=500) private String texto;
    @Column(length=500) private String imagemUrl;
    @Column(nullable=false) private Integer ordem;
    @Column(nullable=false) private boolean correta;
}
