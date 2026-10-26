package sistema.eventos.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(
        name = "tb_trabalho",
        indexes = {
                @Index(name = "idx_trabalho_status", columnList = "status"),
                @Index(name = "idx_trabalho_evento", columnList = "evento_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrabalhoCientifico extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String titulo;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String resumo;

    @Column(length = 500)
    private String coautores; // Ex: "Maria Silva, João Souza" (ajuda no fluxo acadêmico)

    @NotBlank
    @Column(name = "caminho_arquivo", nullable = false, length = 500)
    private String caminhoArquivo; // Caminho do disco do servidor ou URL da nuvem (Ex: Amazon S3)

    @NotNull
    @Builder.Default 
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTrabalho status = StatusTrabalho.SUBMETIDO;

    @Column(columnDefinition = "TEXT")
    private String parecer; // Feedback do avaliador quando o status mudar para ACEITO ou REJEITADO

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;
}