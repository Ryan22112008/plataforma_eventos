package sistema.eventos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_sorteio_resultado", indexes = {
        @Index(name = "idx_sorteio_evento_data", columnList = "evento_id, realizado_em")
})
@Getter
@Setter
@NoArgsConstructor
public class SorteioResultado extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Column(name = "rodada", nullable = false)
    private Integer rodada;

    @Column(name = "nome_vencedor", nullable = false, length = 150)
    private String nomeVencedor;

    @Column(name = "email_vencedor", length = 255)
    private String emailVencedor;

    @Column(name = "percentual_frequencia", nullable = false)
    private Double percentualFrequencia;

    @Column(name = "realizado_em", nullable = false)
    private LocalDateTime realizadoEm;
}
