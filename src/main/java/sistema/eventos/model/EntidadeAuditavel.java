package sistema.eventos.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
public abstract class EntidadeAuditavel {

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @PrePersist
    protected void aoCriar() {
        this.dataCriacao = LocalDateTime.now();
    }

    @PreUpdate
    protected void aoAtualizar() {
        this.dataAtualizacao = LocalDateTime.now();
    }
}
