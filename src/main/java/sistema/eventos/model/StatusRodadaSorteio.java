package sistema.eventos.model;

/**
 * Situação de uma rodada de sorteio. Rodadas desfeitas não são apagadas:
 * ficam canceladas para preservar a auditoria e liberar os vencedores.
 */
public enum StatusRodadaSorteio {

    REALIZADA("Realizada"),
    CANCELADA("Cancelada");

    private final String descricao;

    StatusRodadaSorteio(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
