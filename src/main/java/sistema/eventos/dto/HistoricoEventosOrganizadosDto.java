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
public class HistoricoEventosOrganizadosDto {

    private Long eventoId;
    private String titulo;
    private LocalDate dataInicio;
    private LocalDate dataTermino;
    private String modalidade;
    private String status;
    private String nicho;
    private Integer cargaHoraria;
    private Long totalInscritos;
}