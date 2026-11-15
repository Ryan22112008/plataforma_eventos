package sistema.eventos.model;

/**
 * Ações registradas no log de auditoria dos sorteios.
 */
public enum AcaoAuditoriaSorteio {

    SORTEIO_REALIZADO("Sorteio realizado"),
    RODADA_DESFEITA("Rodada desfeita"),
    PERCENTUAL_ALTERADO("Frequência mínima alterada"),
    STATUS_VENCEDOR_ALTERADO("Status de vencedor alterado"),
    SORTEIO_REPESCAGEM("Sorteio de reposição");

    private final String descricao;

    AcaoAuditoriaSorteio(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
