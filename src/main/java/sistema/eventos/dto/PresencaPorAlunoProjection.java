package sistema.eventos.dto;

/**
 * Projeção agregada de presenças por aluno em um evento.
 * Evita consultas por participante ao montar a lista de inscritos.
 */
public interface PresencaPorAlunoProjection {

    Long getAlunoId();

    Long getTotalPresentes();
}
