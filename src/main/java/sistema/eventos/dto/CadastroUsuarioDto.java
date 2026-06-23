package sistema.eventos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CadastroUsuarioDto {

    @NotBlank(message = "O nome é obrigatório.")
    @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres.")
    private String nome;

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "Insira un e-mail válido.")
    @Size(max = 100, message = "O e-mail não pode passar de 100 caracteres.")
    private String email;

    @NotBlank(message = "A senha é obrigatória.")
    @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres.")
    private String senha;

    // 🎯 O campo chave para o fluxo de aprovação que estruturamos!
    // No HTML, ele se tornará um <input type="checkbox" th:field="*{querSerProfessor}" />
    private boolean querSerProfessor;
}