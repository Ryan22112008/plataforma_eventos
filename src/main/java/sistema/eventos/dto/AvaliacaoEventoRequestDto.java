package sistema.eventos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    @NotNull(message = "A nota é obrigatória.")
    @Min(value = 1, message = "A nota deve ser entre 1 e 5.")
    @Max(value = 5, message = "A nota deve ser entre 1 e 5.")
    private Integer nota;

    @NotBlank(message = "O comentário é obrigatório.")
    @Size(max = 1000, message = "O comentário não pode exceder 1000 caracteres.")
    private String comentario;
}
