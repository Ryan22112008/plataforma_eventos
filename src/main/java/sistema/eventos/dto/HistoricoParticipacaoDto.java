package sistema.eventos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricoParticipacaoDto {

    private Long inscricaoId;
    private Long eventoId;
    private String tituloEvento;
    private LocalDate dataInicio;
    private LocalDate dataTermino;
    private String modalidade;
    private String statusParticipacao;
    private Double percentualFrequencia;
    private boolean certificadoDisponivel;
}