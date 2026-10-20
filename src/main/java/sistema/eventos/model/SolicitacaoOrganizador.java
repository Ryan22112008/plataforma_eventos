package sistema.eventos.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @NotBlank
    @Size(max = 100)
    @Column(name = "nome_completo", nullable = false, length = 100)
    private String nomeCompleto;

    @NotBlank
    @Size(max = 50)
    @Column(name = "matricula", nullable = false, length = 50)
    private String matricula;

    @NotBlank
    @Email
    @Size(max = 100)
    @Column(name = "email_institucional", nullable = false, length = 100)
    private String emailInstitucional;

    @NotBlank
    @Size(max = 1000)
    @Column(name = "justificativa", nullable = false, length = 1000)
    private String justificativa;

    private LocalDateTime dataSolicitacao = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusSolicitacao status = StatusSolicitacao.PENDENTE;

    /**
     * Identifica registros legados (criados antes da Etapa 3) que não possuem
     * os quatro campos obrigatórios preenchidos. Não remove nemrelaxa as validações
     * @NotBlank; apenas permite detectar quem precisa de backfill/correção antes da aprovação.
     */
    public boolean isDadosIncompletos() {
        return nomeCompleto == null || nomeCompleto.isBlank()
                || matricula == null || matricula.isBlank()
                || emailInstitucional == null || emailInstitucional.isBlank()
                || justificativa == null || justificativa.isBlank();
    }
}
