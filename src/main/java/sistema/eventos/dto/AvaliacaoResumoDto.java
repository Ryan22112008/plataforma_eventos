package sistema.eventos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvaliacaoResumoDto {

    private double media;
    private long quantidade;
    private boolean temAvaliacoes;
    private String mediaFormatada;
    private double percentualEstrelas;
}
