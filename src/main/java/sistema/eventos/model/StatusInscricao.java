package sistema.eventos.model;

public enum StatusInscricao {
    /** Inscrição confirmada em evento público. */
    ATIVO,
    /** Solicitação de participação em evento privado aguardando o organizador. */
    PENDENTE,
    /** Solicitação de participação recusada pelo organizador. */
    RECUSADO,
    CANCELADO,
    /** Presença confirmada; libera a declaração de presença. */
    UTILIZADO
}
