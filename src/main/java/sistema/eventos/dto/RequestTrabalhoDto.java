package sistema.eventos.dto;

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
public class RequestTrabalhoDto {

    @NotBlank(message = "O título do trabalho é obrigatório.")
    @Size(max = 200, message = "O título do trabalho não pode exceder 200 caracteres.")
    private String titulo;

    @NotBlank(message = "O resumo do trabalho é obrigatório.")
    private String resumo;

    @NotNull(message = "O ID do evento associado é obrigatório.")
    private Long eventoId;
}
