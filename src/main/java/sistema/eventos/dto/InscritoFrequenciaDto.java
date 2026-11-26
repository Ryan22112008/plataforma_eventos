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

    /** Solicitação de participação em evento privado aguardando aprovação. */
    private boolean solicitacaoPendente;

    /** Solicitação de participação recusada pelo organizador. */
    private boolean inscricaoRecusada;

    /** Sorteios incluem somente inscrições confirmadas e não recusadas. */
    public boolean isInscricaoAprovada() {
        return statusInscricao == StatusInscricao.ATIVO || statusInscricao == StatusInscricao.UTILIZADO;
    }

    public String getRotuloStatus() {
        if (solicitacaoPendente) return "Aguardando aprovação";
        if (inscricaoRecusada) return "Solicitação recusada";
        if (inscricaoCancelada) return "Cancelada";
        if (certificadoLiberado) return "Presença confirmada";
        return "Inscrição ativa";
    }

    public String getFrequenciaFormatada() {
        if (percentualFrequencia == null) {
            return null;
        }
        return Math.round(percentualFrequencia) + "%";
    }
}
