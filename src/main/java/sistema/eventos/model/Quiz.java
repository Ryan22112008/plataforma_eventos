package sistema.eventos.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name="tb_quiz") @Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class Quiz {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="evento_id",nullable=false) private Evento evento;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="organizador_id",nullable=false) private Usuario organizador;
    @Column(nullable=false,length=150) private String titulo;
    @Column(columnDefinition="TEXT") private String descricao;
    @Column(nullable=false,unique=true,length=8) private String codigo;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) @Builder.Default private QuizStatus status=QuizStatus.RASCUNHO;
    @Column(nullable=false) @Builder.Default private Integer pontosFacil=500;
    @Column(nullable=false) @Builder.Default private Integer pontosMedio=1000;
    @Column(nullable=false) @Builder.Default private Integer pontosDificil=1500;
    @Column(nullable=false) @Builder.Default private Integer perguntaAtual=-1;
    private LocalDateTime iniciadoEm;
    private LocalDateTime perguntaLiberadaEm;
    private LocalDateTime encerradoEm;
    @OneToMany(mappedBy="quiz",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("ordem ASC") @Builder.Default private List<QuizPergunta> perguntas=new ArrayList<>();
    @OneToMany(mappedBy="quiz",cascade=CascadeType.ALL,orphanRemoval=true) @Builder.Default private List<QuizJogador> jogadores=new ArrayList<>();
}
