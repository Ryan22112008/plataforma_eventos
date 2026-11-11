package sistema.eventos.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import sistema.eventos.model.StatusPresenca;

/**
 * Uma alteração de presença enviada pelo organizador na tela de frequência.
 * O evento não é confiado ao navegador: ele vem da rota e é validado no
 * service contra a sessão e a inscrição do participante.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlteracaoPresencaRequestDto {

    private Long alunoId;
    private Long sessaoId;
    private StatusPresenca status;
}
