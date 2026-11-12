package sistema.eventos.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name="tb_quiz_pergunta") @Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class QuizPergunta {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="quiz_id",nullable=false) private Quiz quiz;
    @Column(nullable=false,length=1000) private String enunciado;
    @Column(length=500) private String imagemUrl;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private QuizDificuldade dificuldade;
    @Column(nullable=false) @Builder.Default private Integer segundos=20;
    @Column(nullable=false) @Builder.Default private Boolean bonus=false;
    @Column(nullable=false) private Integer ordem;
    @OneToMany(mappedBy="pergunta",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("ordem ASC") @Builder.Default private List<QuizAlternativa> alternativas=new ArrayList<>();
}
