package sistema.eventos.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "tb_evento",
        indexes = {
                @Index(name = "idx_evento_titulo", columnList = "titulo"),
                @Index(name = "idx_evento_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evento extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 150)
    private String titulo;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @NotNull
    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @NotNull
    @Column(name = "data_termino", nullable = false)
    private LocalDate dataTermino;

    @Column(name = "vagas_totais", nullable = false)
    private Integer vagasTotais;

    @Column(name = "vagas_restantes", nullable = false)
    private Integer vagasRestantes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEvento status;

    @Column(name = "local", nullable = false, length = 255)
    private String local;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ModalidadeEvento modalidade;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private NichoEvento nicho;

    @Column(name = "url_google_maps", length = 500)
    private String urlGoogleMaps;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizador_id", nullable = false)
    private Usuario organizador;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Builder.Default
    @Column(name = "carga_horaria", nullable = false)
    private Integer cargaHoraria = 0;

    @Column(name = "banner_url", length = 255)
    private String bannerUrl;

    // --- CORREÇÃO SÊNIOR: Agora guarda apenas o nome/UUID da imagem da frente (.png) ---
    @Column(name = "certificado_template", length = 255)
    private String certificadoTemplate;

    // --- ADICIONADO: Agora o banco consegue armazenar o arquivo do verso enviado pelo formulário ---
    @Column(name = "certificado_template_verso", length = 255)
    private String certificadoTemplateVerso;

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Inscricao> inscricoes;

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrabalhoCientifico> trabalhos;

    // Garante que a lista comece instanciada para o mapeamento do DTO funcionar liso
    @Builder.Default
    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Disciplina> disciplinas = new ArrayList<>();

    @Column(name = "presenca_finalizada")
    private boolean presencaFinalizada = false;

    @Builder.Default
    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Sessao> sessoes = new ArrayList<>();

    public boolean possuiVagasDisponiveis() {
        return this.vagasRestantes != null && this.vagasRestantes > 0;
    }

    public void decrementarVaga() {
        if (!possuiVagasDisponiveis()) {
            throw new IllegalStateException("Não há vagas disponíveis para este evento.");
        }
        this.vagasRestantes--;
    }

    @Override
    protected void aoCriar() {
        super.aoCriar();

        if (this.status == null) {
            this.status = StatusEvento.PUBLICADO;
        }

        if (this.vagasTotais == null) {
            this.vagasTotais = 100;
        }

        if (this.vagasRestantes == null) {
            this.vagasRestantes = this.vagasTotais;
        }

        if (this.local == null || this.local.trim().isEmpty()) {
            this.local = "Auditório SEPLAG-MT";
        }

        if (this.modalidade == null) {
            this.modalidade = ModalidadeEvento.PRESENCIAL;
        }

        // --- FALLBACK SEGURO: Se o professor não fizer upload, o sistema adota arquivos padrão da aplicação ---
        if (this.certificadoTemplate == null || this.certificadoTemplate.trim().isEmpty()) {
            this.certificadoTemplate = "default-frente.png";
        }
        if (this.certificadoTemplateVerso == null || this.certificadoTemplateVerso.trim().isEmpty()) {
            this.certificadoTemplateVerso = "default-verso.png";
        }
    }

}