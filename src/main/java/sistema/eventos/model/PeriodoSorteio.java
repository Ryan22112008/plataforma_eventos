package sistema.eventos.model;

/**
 * Período (turno) utilizado como critério de elegibilidade nos sorteios do evento.
 */
public enum PeriodoSorteio {

    MATUTINO("Matutino"),
    VESPERTINO("Vespertino");

    private final String descricao;

    PeriodoSorteio(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
