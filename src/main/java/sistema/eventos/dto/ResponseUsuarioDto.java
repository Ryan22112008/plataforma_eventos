package sistema.eventos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import sistema.eventos.model.Perfil;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponseUsuarioDto {

    private Long id;
    private String nome;
    private String email;
    private Perfil perfil;
}
