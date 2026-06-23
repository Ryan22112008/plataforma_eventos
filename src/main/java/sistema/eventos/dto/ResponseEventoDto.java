package sistema.eventos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import sistema.eventos.model.StatusEvento;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponseEventoDto {

    private Long id;
    private String titulo;
    private String descricao;
    private LocalDate dataInicio;
    private LocalDate dataTermino;
    private Integer vagasTotais;
    private Integer vagasRestantes;
    private StatusEvento status;
    private String nomeOrganizador; // Evita expor o objeto Usuario inteiro, passa só o nome para a tela
}