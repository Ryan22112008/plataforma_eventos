package sistema.eventos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cada rodada guarda o contexto do sorteio no momento em que ele foi
 * realizado. Isso é importante porque a frequência mínima pode mudar depois,
 * mas o critério usado em uma rodada nunca deve ser reescrito.
 */
@Entity
@Table(name = "tb_sorteio_rodada", indexes = {
        @Index(name = "idx_sorteio_rodada_evento", columnList = "evento_id, numero_rodada"),
        @Index(name = "idx_sorteio_rodada_status", columnList = "evento_id, status")
})
@Getter
@Setter
@NoArgsConstructor
public class SorteioRodada extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Column(name = "numero_rodada", nullable = false)
    private Integer numeroRodada;

    @Column(name = "premio", length = 120)
    private String premio;

    @Column(name = "percentual_minimo", nullable = false)
    private Integer percentualMinimo;

    @Column(name = "total_elegiveis", nullable = false)
    private Integer totalElegiveis;

    @Column(name = "quantidade_sorteada", nullable = false)
    private Integer quantidadeSorteada;

    @Column(name = "realizado_em", nullable = false)
    private LocalDateTime realizadoEm;

    @Column(name = "realizado_por", length = 150)
    private String realizadoPor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusRodadaSorteio status = StatusRodadaSorteio.REALIZADA;

    /**
     * Preenchido quando a rodada é uma reposição de um vencedor ausente.
     * Mantém o vínculo com a rodada original, inclusive o mesmo prêmio.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rodada_origem_id")
    private SorteioRodada rodadaOrigem;

    @OneToMany(mappedBy = "rodadaSorteio")
    @OrderBy("id ASC")
    private List<SorteioResultado> vencedores = new ArrayList<>();

    public boolean isCancelada() {
        return status == StatusRodadaSorteio.CANCELADA;
    }

    public String getPremioExibicao() {
        return premio == null || premio.isBlank() ? "—" : premio;
    }
}
