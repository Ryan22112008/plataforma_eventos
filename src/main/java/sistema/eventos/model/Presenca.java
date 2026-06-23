package sistema.eventos.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_presenca", indexes = {
        @Index(name = "idx_presenca_aluno_sessao", columnList = "usuario_id, sessao_id"),
        @Index(name = "idx_presenca_status", columnList = "status")
})
@Data // Usando Lombok para getters e setters
public class Presenca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario aluno;


    @ManyToOne
    @JoinColumn(name = "sessao_id")
    private Sessao sessao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPresenca status;

    @Column(name = "data_modificacao")
    private LocalDateTime dataModificacao;

    @Column(name = "data_registro", nullable = false, updatable = false)
    private LocalDateTime dataRegistro;
    // Campo útil para auditoria: quem fez a alteração
    private String alteradoPor;

    @PrePersist
    protected void onCreate() {
        this.dataRegistro = LocalDateTime.now();
    }
    @PreUpdate
    protected void onUpdate() {
        this.dataModificacao = LocalDateTime.now();
    }
}