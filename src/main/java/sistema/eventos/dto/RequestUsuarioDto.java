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
public class RequestUsuarioDto {

    @NotBlank(message = "O nome é obrigatório.")
    @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres.")
    private String nome;

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "Informe um e-mail válido.")
    @Size(max = 100, message = "O e-mail não pode exceder 100 caracteres.")
    private String email;

    @NotBlank(message = "A senha é obrigatória.")
    @Size(min = 12, max = 72, message = "A senha deve ter entre 12 e 72 caracteres.")
    private String senha;

    @NotBlank(message = "A confirmação de senha é obrigatória.")
    @Size(max = 72, message = "A confirmação de senha não pode exceder 72 caracteres.")
    private String confirmacaoSenha;

}
