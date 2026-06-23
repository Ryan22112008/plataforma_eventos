package sistema.eventos.dto;

import java.util.List;

public class EventoConfigDTO {
    private String tituloEvento;
    private Integer cargaHorariaTotal;
    private List<DisciplinaDTO> disciplinas;

    // Getters e Setters
    public String getTituloEvento() { return tituloEvento; }
    public void setTituloEvento(String tituloEvento) { this.tituloEvento = tituloEvento; }
    public Integer getCargaHorariaTotal() { return cargaHorariaTotal; }
    public void setCargaHorariaTotal(Integer cargaHorariaTotal) { this.cargaHorariaTotal = cargaHorariaTotal; }
    public List<DisciplinaDTO> getDisciplinas() { return disciplinas; }
    public void setDisciplinas(List<DisciplinaDTO> disciplinas) { this.disciplinas = disciplinas; }
}
