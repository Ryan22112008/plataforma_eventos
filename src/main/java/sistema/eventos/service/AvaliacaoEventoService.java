package sistema.eventos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvaliacaoEventoService {

    private static final int NOTA_MINIMA = 1;
    private static final int NOTA_MAXIMA = 5;
    private static final int COMENTARIO_MAX_CARACTERES = 1000;
    private static final Locale LOCALE_PT_BR = Locale.forLanguageTag("pt-BR");

    private final AvaliacaoEventoRepository avaliacaoEventoRepository;
    private final EventoRepository eventoRepository;
    private final PresencaRepository presencaRepository;

    @Transactional
    public AvaliacaoEvento criar(Long eventoId, Usuario usuarioAutenticado, AvaliacaoEventoRequestDto dto) {
        validarUsuarioAutenticado(usuarioAutenticado);

        Evento evento = buscarEvento(eventoId);
        validarOrganizador(evento, usuarioAutenticado);
        validarParticipacao(eventoId, usuarioAutenticado.getId());

        if (avaliacaoEventoRepository.existsByEventoIdAndUsuarioId(eventoId, usuarioAutenticado.getId())) {
            throw new NegocioException("Você já avaliou este evento.");
        }

        String comentario = validarDados(dto);

        AvaliacaoEvento avaliacao = AvaliacaoEvento.builder()
                .usuario(usuarioAutenticado)
                .evento(evento)
                .nota(dto.getNota())
                .comentario(comentario)
                .build();

        try {
            // O flush antecipa a violação da constraint única e permite traduzi-la
            // para uma mensagem de negócio, inclusive em chamadas concorrentes.
            return avaliacaoEventoRepository.saveAndFlush(avaliacao);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Tentativa de avaliação duplicada para o usuário {} no evento {}.",
                    usuarioAutenticado.getId(), eventoId);
            throw new NegocioException("Você já avaliou este evento.", ex);
        }
    }

    @Transactional
    public AvaliacaoEvento atualizar(Long eventoId, Usuario usuarioAutenticado, AvaliacaoEventoRequestDto dto) {
        validarUsuarioAutenticado(usuarioAutenticado);

        AvaliacaoEvento avaliacao = buscarAvaliacaoDoUsuario(eventoId, usuarioAutenticado.getId());
        String comentario = validarDados(dto);

        avaliacao.setNota(dto.getNota());
        avaliacao.setComentario(comentario);
        return avaliacaoEventoRepository.save(avaliacao);
    }

    @Transactional
    public void excluir(Long eventoId, Usuario usuarioAutenticado) {
        validarUsuarioAutenticado(usuarioAutenticado);

        AvaliacaoEvento avaliacao = buscarAvaliacaoDoUsuario(eventoId, usuarioAutenticado.getId());
        avaliacaoEventoRepository.delete(avaliacao);
    }

    @Transactional(readOnly = true)
    public AvaliacaoResumoDto obterResumo(Long eventoId) {
        Double media = avaliacaoEventoRepository.mediaNotasPorEvento(eventoId);
        long quantidade = avaliacaoEventoRepository.contarPorEvento(eventoId);
        boolean temAvaliacoes = quantidade > 0 && media != null;

        if (!temAvaliacoes) {
            return AvaliacaoResumoDto.builder()
                    .media(0.0)
                    .quantidade(quantidade)
                    .temAvaliacoes(false)
                    .mediaFormatada(null)
                    .percentualEstrelas(0.0)
                    .build();
        }

        return AvaliacaoResumoDto.builder()
                .media(media)
                .quantidade(quantidade)
                .temAvaliacoes(true)
                .mediaFormatada(String.format(LOCALE_PT_BR, "%.1f", media))
                .percentualEstrelas(Math.min(100.0, (media / 5.0) * 100.0))
                .build();
    }

    @Transactional(readOnly = true)
    public Page<AvaliacaoEventoResponseDto> listarDoEvento(Long eventoId, Pageable pageable) {
        return avaliacaoEventoRepository.findByEventoId(eventoId, pageable)
                .map(this::paraResponse);
    }

    @Transactional(readOnly = true)
    public Optional<AvaliacaoEventoResponseDto> buscarDoUsuarioNoEvento(Long eventoId, Long usuarioId) {
        if (eventoId == null || usuarioId == null) {
            return Optional.empty();
        }
        return avaliacaoEventoRepository.findByEventoIdAndUsuarioId(eventoId, usuarioId)
                .map(this::paraResponse);
    }

    /**
     * Informa se o usuário cumpre as regras de participação para avaliar o evento.
     * Reutiliza exatamente a regra de presença confirmada já existente no sistema.
     */
    @Transactional(readOnly = true)
    public boolean podeAvaliar(Evento evento, Long usuarioId) {
        if (evento == null || evento.getId() == null || usuarioId == null) {
            return false;
        }
        if (evento.getOrganizador() != null && usuarioId.equals(evento.getOrganizador().getId())) {
            return false;
        }
        return presencaRepository.countPresencasConfirmadasPorAluno(usuarioId, evento.getId()) > 0;
    }

    private Evento buscarEvento(Long eventoId) {
        if (eventoId == null) {
            throw new NegocioException("Evento não encontrado.");
        }
        return eventoRepository.findById(eventoId)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));
    }

    private void validarUsuarioAutenticado(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            throw new NegocioException("Você precisa estar autenticado para avaliar o evento.");
        }
    }

    private void validarOrganizador(Evento evento, Usuario usuario) {
        if (evento.getOrganizador() != null
                && usuario.getId().equals(evento.getOrganizador().getId())) {
            throw new NegocioException("O organizador do evento não pode avaliar o próprio evento.");
        }
    }

    private void validarParticipacao(Long eventoId, Long usuarioId) {
        long presencasConfirmadas = presencaRepository.countPresencasConfirmadasPorAluno(usuarioId, eventoId);
        if (presencasConfirmadas <= 0) {
            throw new NegocioException("Somente quem participou do evento pode avaliá-lo.");
        }
    }

    private AvaliacaoEvento buscarAvaliacaoDoUsuario(Long eventoId, Long usuarioId) {
        if (eventoId == null || usuarioId == null) {
            throw new NegocioException("Você não possui avaliação para este evento.");
        }
        return avaliacaoEventoRepository.findByEventoIdAndUsuarioId(eventoId, usuarioId)
                .orElseThrow(() -> new NegocioException("Você não possui avaliação para este evento."));
    }

    private String validarDados(AvaliacaoEventoRequestDto dto) {
        if (dto == null || dto.getNota() == null
                || dto.getNota() < NOTA_MINIMA || dto.getNota() > NOTA_MAXIMA) {
            throw new NegocioException("A nota deve ser entre 1 e 5.");
        }

        String comentario = dto.getComentario();
        if (comentario == null || comentario.trim().isEmpty()) {
            throw new NegocioException("O comentário é obrigatório.");
        }

        comentario = comentario.trim();
        if (comentario.length() > COMENTARIO_MAX_CARACTERES) {
            throw new NegocioException("O comentário não pode exceder 1000 caracteres.");
        }
        return comentario;
    }

    private AvaliacaoEventoResponseDto paraResponse(AvaliacaoEvento avaliacao) {
        return AvaliacaoEventoResponseDto.builder()
                .id(avaliacao.getId())
                .nomeUsuario(avaliacao.getUsuario().getNome())
                .nota(avaliacao.getNota())
                .comentario(avaliacao.getComentario())
                .dataAvaliacao(avaliacao.getDataCriacao())
                .build();
    }
}
