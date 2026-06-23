package sistema.eventos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_solicitacao_organizador")
@Getter @Setter
public class SolicitacaoOrganizador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    private LocalDateTime dataSolicitacao = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    private StatusSolicitacao status = StatusSolicitacao.PENDENTE;
}
