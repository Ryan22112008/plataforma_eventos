package sistema.eventos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import sistema.eventos.model.StatusInscricao;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponseInscricaoDto {

    private Long id;
    private String tokenIngresso; // O UUID usado para gerar o QR Code na tela
    private StatusInscricao status;
    private LocalDateTime dataInscricao;

    // Dados achatados (Flattened) para facilitar a renderização no HTML
    private String tituloEvento;
    private String nomeParticipante;
    private String emailParticipante;
}