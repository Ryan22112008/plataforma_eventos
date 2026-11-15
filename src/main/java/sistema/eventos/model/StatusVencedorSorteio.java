package sistema.eventos.model;

/**
 * Situação individual de um vencedor depois da revelação do sorteio.
 */
public enum StatusVencedorSorteio {

    PENDENTE("Pendente"),
    RETIROU("Retirou o prêmio"),
    AUSENTE("Ausente");

    private final String descricao;

    StatusVencedorSorteio(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
