package sistema.eventos.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema.eventos.dto.AvaliacaoEventoRequestDto;
import sistema.eventos.dto.AvaliacaoEventoResponseDto;
import sistema.eventos.dto.AvaliacaoResumoDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.AvaliacaoEvento;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.AvaliacaoEventoRepository;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.PresencaRepository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AvaliacaoEventoService {

    private final AvaliacaoEventoRepository avaliacaoRepository;
    private final PresencaRepository presencaRepository;
    private final EventoRepository eventoRepository;

    public AvaliacaoEventoService(AvaliacaoEventoRepository avaliacaoRepository,
                                  PresencaRepository presencaRepository,
                                  EventoRepository eventoRepository) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.presencaRepository = presencaRepository;
        this.eventoRepository = eventoRepository;
    }

    @Transactional
    public AvaliacaoEvento criar(Long eventoId, Usuario usuarioLogado, AvaliacaoEventoRequestDto dto) {
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new NegocioException("Evento n\u00e3o encontrado."));

        // Regra: organizador n\u00e3o pode avaliar o pr\u00f3prio evento
        if (evento.getOrganizador().getId().equals(usuarioLogado.getId())) {
            throw new NegocioException("O organizador do evento n\u00e3o pode avali\u00e1-lo.");
        }

        // Regra: participacao obrigatoria (reutiliza regra existente de presenca/check-in)
        boolean participou = presencaRepository.countPresencasConfirmadasPorAluno(
                usuarioLogado.getId(), eventoId) > 0;
        if (!participou) {
            throw new NegocioException("Somente quem participou do evento pode avaliá-lo.");
        }

        // Regra: uma avalia\u00e7\u00e3o por usu\u00e1rio
        if (avaliacaoRepository.existsByEventoIdAndUsuarioId(eventoId, usuarioLogado.getId())) {
            throw new NegocioException("Voc\u00ea j\u00e1 avaliou este evento.");
        }

        // Regra: nota de 1 a 5
        if (dto.getNota() == null || dto.getNota() < 1 || dto.getNota() > 5) {
            throw new NegocioException("A nota deve ser entre 1 e 5.");
        }

        AvaliacaoEvento avaliacao = AvaliacaoEvento.builder()
                .usuario(usuarioLogado)
                .evento(evento)
                .nota(dto.getNota())
                .comentario(dto.getComentario())
                .build();
        return avaliacaoRepository.save(avaliacao);
    }

    @Transactional
    public AvaliacaoEvento atualizar(Long eventoId, Usuario usuarioLogado, AvaliacaoEventoRequestDto dto) {
        // Busca s\u00f3 a pr\u00f3pria avalia\u00e7\u00e3o do usu\u00e1rio neste evento (impedi\u00e7\u00e3o de terceiros)
        AvaliacaoEvento avaliacao = avaliacaoRepository.findByEventoIdAndUsuarioId(eventoId, usuarioLogado.getId())
                .orElseThrow(() -> new NegocioException("Voc\u00ea n\u00e3o possui avalia\u00e7\u00e3o para este evento."));

        if (dto.getNota() == null || dto.getNota() < 1 || dto.getNota() > 5) {
            throw new NegocioException("A nota deve ser entre 1 e 5.");
        }

        avaliacao.setNota(dto.getNota());
        avaliacao.setComentario(dto.getComentario());
        return avaliacaoRepository.save(avaliacao);
    }

    @Transactional
    public void excluir(Long eventoId, Usuario usuarioLogado) {
        // Busca s\u00f3 a pr\u00f3pria avalia\u00e7\u00e3o do usu\u00e1rio neste evento
        AvaliacaoEvento avaliacao = avaliacaoRepository.findByEventoIdAndUsuarioId(eventoId, usuarioLogado.getId())
                .orElseThrow(() -> new NegocioException("Voc\u00ea n\u00e3o possui avalia\u00e7\u00e3o para este evento."));
        avaliacaoRepository.delete(avaliacao);
    }

    @Transactional(readOnly = true)
    public AvaliacaoResumoDto resumoDoEvento(Long eventoId) {
        Double media = avaliacaoRepository.mediaNotasPorEvento(eventoId);
        Long quantidade = avaliacaoRepository.contarPorEvento(eventoId);
        boolean tem = quantidade != null && quantidade > 0;
        return AvaliacaoResumoDto.builder()
                .media(tem ? media : 0.0)
                .quantidade(tem ? quantidade : 0L)
                .temAvaliacoes(tem)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AvaliacaoEventoResponseDto> listarDoEvento(Long eventoId) {
        return avaliacaoRepository.findByEventoIdOrderByDataCriacaoDesc(eventoId).stream()
                .map(this::paraResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<AvaliacaoEventoResponseDto> buscarDoUsuarioNoEvento(Long eventoId, Long usuarioId) {
        return avaliacaoRepository.findByEventoIdAndUsuarioId(eventoId, usuarioId).map(this::paraResponse);
    }

    @Transactional(readOnly = true)
    public boolean usuarioPodeAvaliar(Long eventoId, Long usuarioId) {
        Evento evento = eventoRepository.findById(eventoId).orElse(null);
        if (evento == null || evento.getOrganizador().getId().equals(usuarioId)) {
            return false;
        }
        return presencaRepository.countPresencasConfirmadasPorAluno(usuarioId, eventoId) > 0;
    }

    private AvaliacaoEventoResponseDto paraResponse(AvaliacaoEvento a) {
        return AvaliacaoEventoResponseDto.builder()
                .id(a.getId())
                .nomeUsuario(a.getUsuario().getNome())
                .nota(a.getNota())
                .comentario(a.getComentario())
                .dataAvaliacao(a.getDataCriacao())
                .build();
    }
}
