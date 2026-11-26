package sistema.eventos.dto;

import lombok.Builder;
import lombok.Getter;
import sistema.eventos.model.StatusInscricao;

/**
 * Visão de um inscrito para a tela "Ver inscritos" do organizador.
 * A frequência é calculada exclusivamente no backend, a partir das sessões
 * do evento e das presenças com status PRESENTE.
 */
@Getter
@Builder
public class InscritoFrequenciaDto {

    private Long inscricaoId;
    private Long participanteId;
    private String nomeParticipante;
    private String emailParticipante;
    private StatusInscricao statusInscricao;

    private long presencasPresentes;
    private long totalSessoes;

    /**
     * Percentual de frequência. Null quando o evento ainda não possui sessões,
     * situação em que a frequência não é aplicável (evita divisão por zero).
     */
    private Double percentualFrequencia;

    private boolean certificadoLiberado;
    private boolean inscricaoCancelada;

<<<<<<< HEAD
    /** Solicitação de participação em evento privado aguardando aprovação. */
    private boolean solicitacaoPendente;

    /** Solicitação de participação recusada pelo organizador. */
    private boolean inscricaoRecusada;

    public String getRotuloStatus() {
        if (solicitacaoPendente) return "Aguardando aprovação";
        if (inscricaoRecusada) return "Solicitação recusada";
        if (inscricaoCancelada) return "Cancelada";
        if (certificadoLiberado) return "Presença confirmada";
        return "Inscrição ativa";
=======
    public boolean isInscricaoPendente() {
        return statusInscricao == StatusInscricao.PENDENTE;
    }

    public boolean isInscricaoRecusada() {
        return statusInscricao == StatusInscricao.RECUSADO;
    }

    public boolean isInscricaoAprovada() {
        return statusInscricao == StatusInscricao.ATIVO || statusInscricao == StatusInscricao.UTILIZADO;
>>>>>>> 9f8190587fb70577392436388ea40a313988b188
    }

    public String getFrequenciaFormatada() {
        if (percentualFrequencia == null) {
            return null;
        }
        return Math.round(percentualFrequencia) + "%";
    }
}
