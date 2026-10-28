package sistema.eventos.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(
        name = "tb_avaliacao_evento",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_avaliacao_usuario_evento", columnNames = {"usuario_id", "evento_id"})
        },
        indexes = {
                @Index(name = "idx_avaliacao_evento", columnList = "evento_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvaliacaoEvento extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @NotNull
    @Min(1)
    @Max(5)
    @Column(nullable = false)
    private Integer nota;

    @NotBlank
    @Size(max = 1000, message = "O comentário não pode exceder 1000 caracteres.")
    @Column(nullable = false, length = 1000)
    private String comentario;
}
