package sistema.eventos.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="tb_quiz_jogador",uniqueConstraints=@UniqueConstraint(columnNames={"quiz_id","usuario_id"})) @Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class QuizJogador {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="quiz_id",nullable=false) private Quiz quiz;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="usuario_id",nullable=false) private Usuario usuario;
    @Column(nullable=false,length=32) private String avatar;
    @Column(nullable=false) @Builder.Default private Integer pontuacao=0;
    @Column(nullable=false) @Builder.Default private Integer sequencia=0;
    private LocalDateTime entrouEm;
}
