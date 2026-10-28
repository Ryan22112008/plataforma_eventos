package sistema.eventos.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema.eventos.dto.InscritoFrequenciaDto;
import sistema.eventos.dto.PresencaPorAlunoProjection;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.StatusPresenca;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.repository.PresencaRepository;
import sistema.eventos.repository.SessaoRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InscricaoService {

    private final InscricaoRepository inscricaoRepository;
    private final EventoRepository eventoRepository;
    private final SessaoRepository sessaoRepository;
    private final PresencaRepository presencaRepository;

    public InscricaoService(InscricaoRepository inscricaoRepository, EventoRepository eventoRepository,
                            SessaoRepository sessaoRepository, PresencaRepository presencaRepository) {
        this.inscricaoRepository = inscricaoRepository;
        this.eventoRepository = eventoRepository;
        this.sessaoRepository = sessaoRepository;
        this.presencaRepository = presencaRepository;
    }

    @Transactional
    public void inscreverUsuario(Long eventoId, Usuario participante) {
        // 1. CHECAGEM DE DUPLICIDADE EM PRIMEIRO LUGAR ABSOLUTO
        boolean jaExiste = inscricaoRepository.existsByParticipanteIdAndEventoId(participante.getId(), eventoId);
        if (jaExiste) {
            throw new NegocioException("Você já possui uma inscrição activa para este evento.");
        }

        // 2. Busca o evento aplicando o Lock Pessimista para evitar concorrência de vagas
        Evento evento = eventoRepository.findByIdForUpdate(eventoId)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));

        // --- 3. VALIDAÇÃO DE STATUS REFORMULADA (À Prova de Falhas) ---
        // Em vez de exigir PUBLICADO, nós só barramos se o evento estiver explicitamente cancelado
        if (evento.getStatus() != null &&
                (evento.getStatus().name().equals("CANCELADO") || evento.getStatus().name().equals("ENCERRADO"))) {
            throw new NegocioException("Não é possível se inscrever: este evento foi cancelada ou já se encerrou.");
        }

        // --- 4. VALIDAÇÃO DE DATA TOLERANTE ---
        LocalDate hoje = LocalDate.now();

        if (evento.getDataTermino() != null) {
            LocalDate dataFechamento = evento.getDataTermino();

            // Só barra se o dia de hoje já passou da data de término
            if (hoje.isAfter(dataFechamento)) {
                throw new NegocioException("Não é possível se inscrever: este evento já foi encerrado.");
            }
        }

        // Daqui para baixo o código segue igual (Validação de vagas e salvar...)

        // 5. Validação de vagas seguras contra nulos
        if (evento.getVagasRestantes() == null) {
            evento.setVagasRestantes(evento.getVagasTotais() != null ? evento.getVagasTotais() : 100);
        }

        if (evento.getVagasRestantes() <= 0) {
            throw new NegocioException("Não há vagas disponíveis para este evento.");
        }

        // 6. Decrementa a vaga e sincroniza o estado da entidade
        evento.setVagasRestantes(evento.getVagasRestantes() - 1);
        eventoRepository.save(evento);

        // 7. Grava a nova inscrição vinculada de forma limpa
        Inscricao novaInscricao = Inscricao.builder()
                .participante(participante)
                .evento(evento)
                .status(StatusInscricao.ATIVO)
                .build();

        inscricaoRepository.save(novaInscricao);
    }

    // --- Célula de Portaria / Check-in via QR Code ---
    @Transactional
    public void realizarCheckIn(String tokenIngresso, String actorEmail, boolean admin) {
        Inscricao inscricao = inscricaoRepository.findByTokenIngressoComRelacoes(tokenIngresso)
                .orElseThrow(() -> new NegocioException("Ingresso inválido: QR Code não encontrado no sistema."));

        if (!admin && (actorEmail == null || inscricao.getEvento().getOrganizador() == null
                || !inscricao.getEvento().getOrganizador().getEmail().equalsIgnoreCase(actorEmail))) {
            throw new org.springframework.security.access.AccessDeniedException("Você não pode registrar check-in para este evento.");
        }

        if (inscricao.getStatus() == StatusInscricao.CANCELADO) {
            throw new NegocioException("Check-in negado: Esta inscrição foi cancelada pelo usuário ou organizador.");
        }

        if (inscricao.getStatus() == StatusInscricao.UTILIZADO) {
            throw new NegocioException("Alerta de fraude: Este ingresso já foi utilizado para realizar check-in.");
        }

        inscricao.realizarCheckIn();
    }

    @Transactional(readOnly = true)
    public List<Inscricao> listarInscricoesDoUsuario(Long participanteId) {
        return inscricaoRepository.findByParticipanteIdComEvento(participanteId);
    }

    /**
     * --- ADICIONADO (Sobrecarga Sênior): Adaptador para o ParticipanteController ---
     * Permite passar o objeto 'Usuario' diretamente para manter a semântica limpa do Controller.
     */
    @Transactional(readOnly = true)
    public List<Inscricao> listarPorUsuario(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            throw new IllegalArgumentException("Usuário inválido para listagem de inscrições.");
        }
        return inscricaoRepository.findByParticipante(usuario);
    }

    @Transactional(readOnly = true)
    public List<Inscricao> listarInscritosDoEvento(Long eventoId) {
        return inscricaoRepository.findByEventoId(eventoId);
    }

    @Transactional(readOnly = true)
    public Inscricao buscarPorId(Long id) {
        return inscricaoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Inscrição não encontrada com o ID: " + id));
    }
    public List<Inscricao> buscarCertificadosDoUsuario(Long usuarioId) {
        return inscricaoRepository.findByParticipanteIdAndStatus(usuarioId, StatusInscricao.UTILIZADO);
    }
    @Transactional
    public void confirmarPresenca(Long inscricaoId) {
        Inscricao inscricao = inscricaoRepository.findById(inscricaoId)
                .orElseThrow(() -> new NegocioException("Inscrição não encontrada no sistema."));

        if (inscricao.getStatus() == StatusInscricao.CANCELADO) {
            throw new NegocioException("Não é possível confirmar presença em uma inscrição cancelada.");
        }
        inscricao.setStatus(StatusInscricao.UTILIZADO);
        inscricao.setDataCheckIn(java.time.LocalDateTime.now());

        inscricaoRepository.save(inscricao);
    }
    @Transactional(readOnly = true)
    public List<Inscricao> listarPorEvento(Long eventoId) {
        // CORRIGIDO: Chama o método do repositório que busca por ID do Evento
        return inscricaoRepository.findByEventoId(eventoId);
    }

    /**
     * Monta a lista de inscritos com a frequência calculada no backend.
     * Usa três consultas no total (inscrições, contagem de sessões e presenças
     * agrupadas por aluno), independentemente da quantidade de participantes.
     */
    @Transactional(readOnly = true)
    public List<InscritoFrequenciaDto> listarInscritosComFrequencia(Long eventoId) {
        List<Inscricao> inscricoes = inscricaoRepository.findByEventoId(eventoId);
        long totalSessoes = sessaoRepository.countByEventoId(eventoId);

        Map<Long, Long> presencasPorAluno = presencaRepository
                .contarPresencasPresentesPorAluno(eventoId, StatusPresenca.PRESENTE)
                .stream()
                .filter(projecao -> projecao.getAlunoId() != null)
                .collect(Collectors.toMap(
                        PresencaPorAlunoProjection::getAlunoId,
                        PresencaPorAlunoProjection::getTotalPresentes));

        return inscricoes.stream()
                .map(inscricao -> montarInscritoFrequencia(inscricao, totalSessoes, presencasPorAluno))
                .toList();
    }

    private InscritoFrequenciaDto montarInscritoFrequencia(Inscricao inscricao,
                                                           long totalSessoes,
                                                           Map<Long, Long> presencasPorAluno) {
        Usuario participante = inscricao.getParticipante();
        Long participanteId = participante != null ? participante.getId() : null;
        long presencasPresentes = participanteId != null
                ? presencasPorAluno.getOrDefault(participanteId, 0L)
                : 0L;

        Double percentualFrequencia = totalSessoes > 0
                ? (presencasPresentes * 100.0) / totalSessoes
                : null;

        StatusInscricao status = inscricao.getStatus();

        return InscritoFrequenciaDto.builder()
                .inscricaoId(inscricao.getId())
                .participanteId(participanteId)
                .nomeParticipante(participante != null ? participante.getNome() : "Usuário sem nome")
                .emailParticipante(participante != null ? participante.getEmail() : null)
                .statusInscricao(status)
                .presencasPresentes(presencasPresentes)
                .totalSessoes(totalSessoes)
                .percentualFrequencia(percentualFrequencia)
                .certificadoLiberado(status == StatusInscricao.UTILIZADO)
                .inscricaoCancelada(status == StatusInscricao.CANCELADO)
                .build();
    }

    /**
     * Valida a participação de um inscrito. A inscrição é resolvida no banco
     * pelo seu ID e precisa pertencer ao evento informado; nenhum dado crítico
     * (participante, percentual ou status) é aceito do navegador.
     * A liberação do certificado reutiliza a regra existente:
     * Inscricao.status == UTILIZADO.
     */
    @Transactional
    public Inscricao validarParticipacaoDoParticipante(Long eventoId, Long inscricaoId) {
        Inscricao inscricao = inscricaoRepository.findById(inscricaoId)
                .orElseThrow(() -> new NegocioException("Inscrição não encontrada no sistema."));

        if (inscricao.getEvento() == null || !eventoId.equals(inscricao.getEvento().getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Inscrição não pertence a este evento.");
        }
        if (inscricao.getParticipante() == null) {
            throw new NegocioException("A inscrição não possui participante vinculado.");
        }
        if (inscricao.getStatus() == StatusInscricao.CANCELADO) {
            throw new NegocioException("Não é possível validar uma inscrição cancelada.");
        }

        if (inscricao.getStatus() != StatusInscricao.UTILIZADO) {
            inscricao.setStatus(StatusInscricao.UTILIZADO);
            if (inscricao.getDataCheckIn() == null) {
                inscricao.setDataCheckIn(LocalDateTime.now());
            }
            inscricaoRepository.save(inscricao);
        }
        return inscricao;
    }

    /** Valida um conjunto de inscrições numa única transação e persiste válidas em lote. */
    @Transactional
    public int validarParticipacoesEmLote(Long eventoId, List<Long> inscricaoIds) {
        if (inscricaoIds == null || inscricaoIds.isEmpty()) return 0;
        List<Long> ids = new LinkedHashSet<>(inscricaoIds).stream().filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return 0;

        LocalDateTime agora = LocalDateTime.now();
        List<Inscricao> validas = inscricaoRepository.findAllByIdComRelacoes(ids).stream()
                .filter(inscricao -> inscricao.getEvento() != null
                        && eventoId.equals(inscricao.getEvento().getId()))
                .filter(inscricao -> inscricao.getParticipante() != null
                        && inscricao.getParticipante().getId() != null)
                .filter(inscricao -> inscricao.getStatus() != null
                        && inscricao.getStatus() != StatusInscricao.CANCELADO
                        && inscricao.getStatus() != StatusInscricao.UTILIZADO)
                .toList();

        validas.forEach(inscricao -> {
            inscricao.setStatus(StatusInscricao.UTILIZADO);
            if (inscricao.getDataCheckIn() == null) inscricao.setDataCheckIn(agora);
        });
        if (!validas.isEmpty()) inscricaoRepository.saveAll(validas);
        return validas.size();
    }

    @Transactional
    public void fecharDiarioClasse(Long eventoId, List<Long> idsPresentes) {
        // 1. Recupera o evento ou lança erro se não existir
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));

        // 2. Busca todas as inscrições vinculadas a este evento específico
        List<Inscricao> todasInscricoes = inscricaoRepository.findByEventoId(eventoId);

        // 3. Varre a lista aplicando a regra de presença em lote (Estilo SEDUC)
        todasInscricoes.forEach(inscricao -> {
            if (idsPresentes != null && idsPresentes.contains(inscricao.getId())) {

                // SE FOI SELECIONADO: Altera o status para UTILIZADO (Presença Confirmada)
                // Nota: Altere 'InscricaoStatus' para o nome exato do seu Enum de Status se for diferente
                inscricao.setStatus(StatusInscricao.UTILIZADO);
                inscricao.setDataCheckIn(java.time.LocalDateTime.now());

            } else {

                // SE NÃO FOI SELECIONADO: Permanece ou volta para ATIVO (Pendente/Falta)
                inscricao.setStatus(StatusInscricao.ATIVO);
                inscricao.setDataCheckIn(null); // Remove data de check-in caso o professor mude de ideia

            }
        });

        // 4. Homologa/Trava o diário oficial do evento para auditoria institucional
        evento.setPresencaFinalizada(true);

        // 5. Persiste todas as alterações em lote de forma atômica no banco de dados
        inscricaoRepository.saveAll(todasInscricoes);
        eventoRepository.save(evento);
    }
    @Transactional
    public void alternarPresenca(Long inscricaoId) {
        Inscricao inscricao = inscricaoRepository.findById(inscricaoId)
                .orElseThrow(() -> new RuntimeException("Inscrição não encontrada"));

        // Se está UTILIZADO (presente), volta para ATIVO (pendente).
        // Se está ATIVO, muda para UTILIZADO.
        if (inscricao.getStatus() == StatusInscricao.UTILIZADO) {
            inscricao.setStatus(StatusInscricao.ATIVO);
            inscricao.setDataCheckIn(null);
        } else {
            if (inscricao.getStatus() == StatusInscricao.CANCELADO) {
                throw new NegocioException("Não é possível confirmar presença em uma inscrição cancelada.");
            }
            inscricao.setStatus(StatusInscricao.UTILIZADO);
            inscricao.setDataCheckIn(java.time.LocalDateTime.now());
        }
        inscricaoRepository.save(inscricao);
    }
}
