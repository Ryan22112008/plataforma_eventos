package sistema.eventos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro imutável das ações realizadas na aba Sorteios.
 * A interface exibe este log apenas para consulta.
 */
@Entity
@Table(name = "tb_sorteio_auditoria", indexes = {
        @Index(name = "idx_sorteio_auditoria_evento_data", columnList = "evento_id, realizado_em")
})
@Getter
@Setter
@NoArgsConstructor
public class SorteioAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Enumerated(EnumType.STRING)
    @Column(name = "acao", nullable = false, length = 40)
    private AcaoAuditoriaSorteio acao;

    @Column(name = "detalhes", nullable = false, columnDefinition = "TEXT")
    private String detalhes;

    @Column(name = "autor_email", length = 150)
    private String autorEmail;

    @Column(name = "autor_nome", length = 150)
    private String autorNome;

    @Column(name = "realizado_em", nullable = false)
    private LocalDateTime realizadoEm;
}
