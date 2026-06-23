package sistema.eventos.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "tb_inscricao",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_usuario_evento", columnNames = {"usuario_id", "evento_id"})
        },
        indexes = {
                @Index(name = "idx_inscricao_token", columnList = "token_ingresso") // Busca rápida no check-in pelo QR Code
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inscricao extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario participante;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    // --- NOVO: Token único gerado para virar o QR Code ---
    @NotBlank
    @Column(name = "token_ingresso", nullable = false, unique = true, length = 100)
    @Builder.Default
    private String tokenIngresso = UUID.randomUUID().toString();

    // --- NOVO: Status do Ingresso ---
    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusInscricao status = StatusInscricao.ATIVO; // ATIVO, CANCELADO, UTILIZADO (Check-in feito)

    // --- NOVO: Data do Check-in ---
    @Column(name = "data_check_in")
    private LocalDateTime dataCheckIn;

     public void realizarCheckIn() {
        if (this.status == StatusInscricao.UTILIZADO) {
            throw new IllegalStateException("Este ingresso já foi utilizado!");
        }
        if (this.status == StatusInscricao.CANCELADO) {
            throw new IllegalStateException("Este ingresso está cancelado!");
        }
        this.status = StatusInscricao.UTILIZADO;
        this.dataCheckIn = LocalDateTime.now();
    }

}