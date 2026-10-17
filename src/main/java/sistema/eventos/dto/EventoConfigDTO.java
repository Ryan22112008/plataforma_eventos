package sistema.eventos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public class EventoConfigDTO {
    @NotBlank
    @Size(max = 150)
    private String tituloEvento;
    @NotNull
    @Min(1)
    private Integer cargaHorariaTotal;
    @Size(max = 100)
    private List<DisciplinaDTO> disciplinas;

    // Getters e Setters
    public String getTituloEvento() { return tituloEvento; }
    public void setTituloEvento(String tituloEvento) { this.tituloEvento = tituloEvento; }
    public Integer getCargaHorariaTotal() { return cargaHorariaTotal; }
    public void setCargaHorariaTotal(Integer cargaHorariaTotal) { this.cargaHorariaTotal = cargaHorariaTotal; }
    public List<DisciplinaDTO> getDisciplinas() { return disciplinas; }
    public void setDisciplinas(List<DisciplinaDTO> disciplinas) { this.disciplinas = disciplinas; }
}
