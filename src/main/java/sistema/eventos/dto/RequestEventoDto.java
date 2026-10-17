package sistema.eventos.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile; // ADICIONADO
import sistema.eventos.model.ModalidadeEvento;

import java.time.LocalDate;
import java.util.ArrayList; // ADICIONADO
import java.util.List; // ADICIONADO

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestEventoDto {

    private Long id;

    @NotBlank(message = "O título do evento é obrigatório.")
    @Size(max = 150, message = "O título não pode exceder 150 caracteres.")
    private String titulo;

    @NotBlank(message = "A descrição do evento é obrigatória.")
    private String descricao;

    @NotNull(message = "A data de início é obrigatória.")
    @FutureOrPresent(message = "A data de início não pode ser no passado.")
    private LocalDate dataInicio;

    @NotNull(message = "A data de término é obrigatória.")
    @FutureOrPresent(message = "A data de término não pode ser no passado.")
    private LocalDate dataTermino;

    private Integer vagasTotais;

    private String local;

    @NotNull(message = "A modalidade é obrigatória.")
    private ModalidadeEvento modalidade;

    @NotBlank(message = "O nicho/área do evento é obrigatório.")
    @Size(max = 100, message = "O nicho não pode exceder 100 caracteres.")
    private String nicho;

    @Size(max = 500, message = "O link do Google Maps não pode exceder 500 caracteres.")
    private String urlGoogleMaps;

    private Double latitude;
    private Double longitude;

    @NotNull(message = "A carga horária é obrigatória.")
    @Min(value = 1, message = "A carga horária deve ser de no mínimo 1 hora.")
    private Integer cargaHoraria;

    // --- NOVOS CAMPOS SÊNIOR ADICIONADOS PARA COMPATIBILIDADE COM O CANVA E FORMULÁRIO ---

    private MultipartFile imagemFrente;

    private MultipartFile imagemVerso;

    // Nomes das artes já salvas; usados somente para exibir a imagem atual no formulário.
    private String bannerAtual;
    private String imagemFrenteAtual;
    private String imagemVersoAtual;

    @Builder.Default // Proteção para o Lombok não quebrar a inicialização da lista em cenários de teste
    private List<DisciplinaDTO> disciplinas = new ArrayList<>();
}
