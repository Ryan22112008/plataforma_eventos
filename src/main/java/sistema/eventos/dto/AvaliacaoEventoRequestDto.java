package sistema.eventos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvaliacaoEventoRequestDto {

    @NotNull(message = "A nota \u00e9 obrigat\u00f3ria.")
    @Min(value = 1, message = "A nota deve ser de 1 a 5.")
    @Max(value = 5, message = "A nota deve ser de 1 a 5.")
    private Integer nota;

    @Size(max = 1000, message = "O coment\u00e1rio n\u00e3o pode exceder 1000 caracteres.")
    private String comentario;
}
