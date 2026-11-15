package sistema.eventos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_sorteio_resultado", indexes = {
        @Index(name = "idx_sorteio_evento_data", columnList = "evento_id, realizado_em"),
        @Index(name = "idx_sorteio_resultado_rodada", columnList = "rodada_id"),
        @Index(name = "idx_sorteio_resultado_status", columnList = "status_vencedor")
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

    /**
     * Metadados completos da rodada. Registros antigos podem não ter este
     * vínculo; a interface trata esses casos sem quebrar o histórico.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rodada_id")
    private SorteioRodada rodadaSorteio;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_vencedor", length = 30)
    private StatusVencedorSorteio statusVencedor = StatusVencedorSorteio.PENDENTE;

    /**
     * Quando este vencedor faltou, aponta para o resultado da reposição.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "substituido_por_id")
    private SorteioResultado substituidoPor;

    public StatusVencedorSorteio getStatusVencedorEfetivo() {
        return statusVencedor == null ? StatusVencedorSorteio.PENDENTE : statusVencedor;
    }

    public boolean isRodadaCancelada() {
        return rodadaSorteio != null && rodadaSorteio.isCancelada();
    }

    public boolean isRodadaAtiva() {
        return rodadaSorteio == null || !rodadaSorteio.isCancelada();
    }

    public String getPremioRodada() {
        return rodadaSorteio == null ? null : rodadaSorteio.getPremio();
    }

    /**
     * Texto do critério congelado no momento da rodada. Para registros
     * anteriores à melhoria, não inventa dados que não foram gravados.
     */
    public String getCriterioAplicado() {
        if (rodadaSorteio == null) {
            return "Registro anterior — critério não detalhado";
        }
        return "Sorteado entre " + rodadaSorteio.getTotalElegiveis()
                + " elegíveis com frequência ≥ " + rodadaSorteio.getPercentualMinimo() + "%";
    }

    public String getStatusExibicao() {
        if (isRodadaCancelada()) {
            return "Rodada cancelada";
        }
        return getStatusVencedorEfetivo().getDescricao();
    }
}
