package sistema.eventos.dto;

/**
 * Projeção usada na exportação do Super Admin para carregar, em lote, os
 * títulos de eventos relacionados a cada usuário (participados ou organizados).
 * Evita consultas por usuário e mantém a exportação em poucas queries.
 */
public interface UsuarioEventoTituloProjection {

    Long getUsuarioId();

    String getTitulo();
}
