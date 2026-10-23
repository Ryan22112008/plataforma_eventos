package sistema.eventos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestSolicitacaoOrganizadorDto {

    @NotBlank(message = "O nome completo é obrigatório.")
    @Size(min = 3, max = 100, message = "O nome completo deve ter entre 3 e 100 caracteres.")
    private String nomeCompleto;

    @NotBlank(message = "A matrícula é obrigatória.")
    @Size(max = 50, message = "A matrícula não pode exceder 50 caracteres.")
    private String matricula;

    @NotBlank(message = "O e-mail institucional é obrigatório.")
    @Email(message = "Informe um e-mail institucional válido.")
    @Size(max = 100, message = "O e-mail institucional não pode exceder 100 caracteres.")
    private String emailInstitucional;

    @NotBlank(message = "A justificativa é obrigatória.")
    @Size(max = 1000, message = "A justificativa não pode exceder 1000 caracteres.")
    private String justificativa;

    @NotBlank(message = "O órgão é obrigatório.")
    @Size(max = 100, message = "O órgão não pode exceder 100 caracteres.")
    private String orgao;
}