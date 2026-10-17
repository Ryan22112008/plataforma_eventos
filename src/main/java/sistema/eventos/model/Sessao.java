package sistema.eventos.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Entity
@Table(name = "tb_sessao")
@Data
public class Sessao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome; // Ex: "Aula Matutina", "Oficina Prática"

    private LocalDateTime dataHoraInicio;
    private LocalDateTime dataHoraFim;

    // O código que o aluno vai escanear
    private String qrCodeToken;

    // Relacionamento com o seu objeto de curso/evento
    @ManyToOne
    @JoinColumn(name = "evento_id")
    private Evento evento;

    // Opcional: lista de presenças vinculadas a esta sessão
    @OneToMany(mappedBy = "sessao", cascade = CascadeType.ALL)
    private List<Presenca> presencas;

    // Método para validar se a sessão ainda está ativa
    public boolean isAtiva() {
        LocalDateTime agora = LocalDateTime.now(ZoneId.of("America/Cuiaba"));
        return agora.isAfter(dataHoraInicio) && agora.isBefore(dataHoraFim);
    }
}
