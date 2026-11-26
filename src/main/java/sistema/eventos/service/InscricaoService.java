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
import sistema.eventos.model.SorteioResultado;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.repository.PresencaRepository;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.repository.SorteioResultadoRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.security.SecureRandom;
import java.util.stream.Collectors;

@Service
public class InscricaoService {

    /** Limite defensivo para o lote de validação de participação. */
    private static final int LIMITE_VALIDACOES_POR_LOTE = 1000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InscricaoRepository inscricaoRepository;
    private final EventoRepository eventoRepository;
    private final SessaoRepository sessaoRepository;
    private final PresencaRepository presencaRepository;
    private final SorteioResultadoRepository sorteioResultadoRepository;

    public InscricaoService(InscricaoRepository inscricaoRepository, EventoRepository eventoRepository,
                            SessaoRepository sessaoRepository, PresencaRepository presencaRepository,
                            SorteioResultadoRepository sorteioResultadoRepository) {
        this.inscricaoRepository = inscricaoRepository;
        this.eventoRepository = eventoRepository;
        this.sessaoRepository = sessaoRepository;
        this.presencaRepository = presencaRepository;
        this.sorteioResultadoRepository = sorteioResultadoRepository;
    }

    /**
     * Inscreve em evento público ou registra uma solicitação em evento privado.
     *
     * <p>Em eventos públicos a vaga é reservada imediatamente. Em eventos
     * privados a inscrição fica como {@link StatusInscricao#PENDENTE} e a vaga
     * só é consumida quando o organizador aprova a solicitação.</p>
     */
    @Transactional
    public StatusInscricao inscreverUsuario(Long eventoId, Usuario participante) {
<<<<<<< HEAD
        if (participante == null || participante.getId() == null) {
            throw new NegocioException("Usuário inválido para inscrição.");
=======
        // 1. CHECAGEM DE DUPLICIDADE EM PRIMEIRO LUGAR ABSOLUTO
        Optional<Inscricao> existente = inscricaoRepository
                .findByParticipanteIdAndEventoIdComEvento(participante.getId(), eventoId);
        if (existente.isPresent()) {
            StatusInscricao st = existente.get().getStatus();
            if (st == StatusInscricao.PENDENTE) {
                throw new NegocioException("Sua solicitação de participação já foi enviada e aguarda decisão do organizador.");
            }
            if (st == StatusInscricao.RECUSADO) {
                throw new NegocioException("Sua solicitação de participação não foi aprovada pelo organizador deste evento.");
            }
            throw new NegocioException("Você já possui uma inscrição ativa para este evento.");
>>>>>>> 9f8190587fb70577392436388ea40a313988b188
        }

        // Lock pessimista evita overbooking em inscrições simultâneas.
        Evento evento = eventoRepository.findByIdForUpdate(eventoId)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));

<<<<<<< HEAD
        validarEventoAbertoParaInscricao(evento);
        boolean privado = evento.isEventoPrivado();

        Optional<Inscricao> existente = inscricaoRepository
                .findByParticipanteIdAndEventoIdComEvento(participante.getId(), eventoId);

        if (existente.isPresent()) {
            Inscricao inscricao = existente.get();
            if (inscricao.getStatus() == StatusInscricao.PENDENTE) {
                throw new NegocioException("Sua solicitação de participação já está aguardando aprovação do organizador.");
            }
            if (inscricao.getStatus() == StatusInscricao.ATIVO || inscricao.getStatus() == StatusInscricao.UTILIZADO) {
                throw new NegocioException("Você já possui uma inscrição ativa para este evento.");
            }

            // Inscrições canceladas/recusadas podem ser reabertas.
            inscricao.setDataCheckIn(null);
            inscricao.setCertificadoGeradoEm(null);
            if (privado) {
                inscricao.setStatus(StatusInscricao.PENDENTE);
                inscricaoRepository.save(inscricao);
                return StatusInscricao.PENDENTE;
            }

            reservarVaga(evento);
            inscricao.setStatus(StatusInscricao.ATIVO);
            inscricaoRepository.save(inscricao);
            return StatusInscricao.ATIVO;
        }

=======
        // --- 3. VALIDAÇÃO DE STATUS ---
        if (evento.getStatus() != null &&
                (evento.getStatus().name().equals("CANCELADO") || evento.getStatus().name().equals("ENCERRADO"))) {
            throw new NegocioException("Não é possível se inscrever: este evento foi cancelado ou já se encerrou.");
        }

        // --- 4. VALIDAÇÃO DE DATA ---
        LocalDate hoje = LocalDate.now();
        if (evento.getDataTermino() != null && hoje.isAfter(evento.getDataTermino())) {
            throw new NegocioException("Não é possível se inscrever: este evento já foi encerrado.");
        }

        // 5. Validação de vagas seguras contra nulos
        if (evento.getVagasRestantes() == null) {
            evento.setVagasRestantes(evento.getVagasTotais() != null ? evento.getVagasTotais() : 100);
        }

        if (evento.getVagasRestantes() <= 0) {
            throw new NegocioException("Não há vagas disponíveis para este evento.");
        }

        // Evento privado: cria solicitação PENDENTE para o organizador decidir (sem consumir vaga ainda)
        if (evento.isPrivado()) {
            Inscricao solicitacao = Inscricao.builder()
                    .participante(participante)
                    .evento(evento)
                    .status(StatusInscricao.PENDENTE)
                    .build();
            inscricaoRepository.save(solicitacao);
            return StatusInscricao.PENDENTE;
        }

        // Evento público: decrementa vaga imediatamente e ativa inscrição
        evento.setVagasRestantes(evento.getVagasRestantes() - 1);
        eventoRepository.save(evento);

>>>>>>> 9f8190587fb70577392436388ea40a313988b188
        Inscricao novaInscricao = Inscricao.builder()
                .participante(participante)
                .evento(evento)
                .status(privado ? StatusInscricao.PENDENTE : StatusInscricao.ATIVO)
                .build();

        if (!privado) {
            reservarVaga(evento);
        }

        inscricaoRepository.save(novaInscricao);
<<<<<<< HEAD
        return novaInscricao.getStatus();
    }

    private void validarEventoAbertoParaInscricao(Evento evento) {
        if (evento.getStatus() != null &&
                (evento.getStatus().name().equals("CANCELADO") || evento.getStatus().name().equals("ENCERRADO"))) {
            throw new NegocioException("Não é possível se inscrever: este evento foi cancelado ou já se encerrou.");
        }
        if (evento.getDataTermino() != null && LocalDate.now().isAfter(evento.getDataTermino())) {
            throw new NegocioException("Não é possível se inscrever: este evento já foi encerrado.");
        }
    }

    private void reservarVaga(Evento evento) {
=======
        return StatusInscricao.ATIVO;
    }

    @Transactional
    public void aprovarParticipacao(Long eventoId, Long inscricaoId) {
        Evento evento = eventoRepository.findByIdForUpdate(eventoId)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));
        Inscricao inscricao = inscricaoRepository.findById(inscricaoId)
                .orElseThrow(() -> new NegocioException("Inscrição não encontrada."));
        if (inscricao.getEvento() == null || !eventoId.equals(inscricao.getEvento().getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Inscrição não pertence a este evento.");
        }
        if (inscricao.getStatus() == StatusInscricao.CANCELADO) {
            throw new NegocioException("Não é possível aprovar uma inscrição cancelada pelo participante.");
        }
        if (inscricao.getStatus() == StatusInscricao.ATIVO || inscricao.getStatus() == StatusInscricao.UTILIZADO) {
            return;
        }
>>>>>>> 9f8190587fb70577392436388ea40a313988b188
        if (evento.getVagasRestantes() == null) {
            evento.setVagasRestantes(evento.getVagasTotais() != null ? evento.getVagasTotais() : 100);
        }
        if (evento.getVagasRestantes() <= 0) {
<<<<<<< HEAD
            throw new NegocioException("Não há vagas disponíveis para este evento.");
        }
        evento.setVagasRestantes(evento.getVagasRestantes() - 1);
        eventoRepository.save(evento);
    }

    /**
     * Aprova uma solicitação pendente de evento privado. A vaga só é
     * consumida neste momento e a operação é bloqueada se o evento lotar.
     */
    @Transactional
    public Inscricao aprovarSolicitacao(Long eventoId, Long inscricaoId, String emailOrganizador) {
        Evento evento = eventoRepository.findByIdForUpdate(eventoId)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));
        validarOrganizador(evento, emailOrganizador);

        Inscricao inscricao = inscricaoRepository.findById(inscricaoId)
                .orElseThrow(() -> new NegocioException("Solicitação de participação não encontrada."));
        if (inscricao.getEvento() == null || !eventoId.equals(inscricao.getEvento().getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "A solicitação não pertence a este evento.");
        }
        if (inscricao.getStatus() != StatusInscricao.PENDENTE) {
            throw new NegocioException("Esta solicitação não está aguardando aprovação.");
        }

        reservarVaga(evento);
        inscricao.setStatus(StatusInscricao.ATIVO);
        inscricao.setDataCheckIn(null);
        return inscricaoRepository.save(inscricao);
    }

    /** Recusa uma solicitação pendente sem consumir vaga. */
    @Transactional
    public Inscricao recusarSolicitacao(Long eventoId, Long inscricaoId, String emailOrganizador) {
        Evento evento = eventoRepository.findByIdForUpdate(eventoId)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));
        validarOrganizador(evento, emailOrganizador);

        Inscricao inscricao = inscricaoRepository.findById(inscricaoId)
                .orElseThrow(() -> new NegocioException("Solicitação de participação não encontrada."));
        if (inscricao.getEvento() == null || !eventoId.equals(inscricao.getEvento().getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "A solicitação não pertence a este evento.");
        }
        if (inscricao.getStatus() != StatusInscricao.PENDENTE) {
            throw new NegocioException("Esta solicitação não está aguardando aprovação.");
        }

        inscricao.setStatus(StatusInscricao.RECUSADO);
        return inscricaoRepository.save(inscricao);
    }

    private void validarOrganizador(Evento evento, String emailOrganizador) {
        if (emailOrganizador == null) {
            return; // SUPER_ADMIN já autorizado na camada de controller
        }
        if (evento.getOrganizador() == null
                || !evento.getOrganizador().getEmail().equalsIgnoreCase(emailOrganizador)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você não tem acesso a este evento.");
        }
=======
            throw new NegocioException("Não há vagas disponíveis para aprovar mais participantes.");
        }
        evento.setVagasRestantes(evento.getVagasRestantes() - 1);
        eventoRepository.save(evento);
        inscricao.setStatus(StatusInscricao.ATIVO);
        inscricaoRepository.save(inscricao);
    }

    @Transactional
    public void recusarParticipacao(Long eventoId, Long inscricaoId) {
        Evento evento = eventoRepository.findByIdForUpdate(eventoId)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));
        Inscricao inscricao = inscricaoRepository.findById(inscricaoId)
                .orElseThrow(() -> new NegocioException("Inscrição não encontrada."));
        if (inscricao.getEvento() == null || !eventoId.equals(inscricao.getEvento().getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Inscrição não pertence a este evento.");
        }
        if (inscricao.getStatus() == StatusInscricao.UTILIZADO) {
            throw new NegocioException("Não é possível recusar um participante com presença já validada.");
        }
        if (inscricao.getStatus() == StatusInscricao.CANCELADO) {
            throw new NegocioException("Esta inscrição foi cancelada pelo participante.");
        }
        if (inscricao.getStatus() == StatusInscricao.ATIVO) {
            int atuais = evento.getVagasRestantes() == null ? 0 : evento.getVagasRestantes();
            int maximo = evento.getVagasTotais() == null ? atuais + 1 : evento.getVagasTotais();
            evento.setVagasRestantes(Math.min(maximo, atuais + 1));
            eventoRepository.save(evento);
        }
        inscricao.setStatus(StatusInscricao.RECUSADO);
        inscricaoRepository.save(inscricao);
    }

    @Transactional
    public void autorizarParticipante(Long eventoId, Usuario participante) {
        Evento evento = eventoRepository.findByIdForUpdate(eventoId)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));
        Optional<Inscricao> existente = inscricaoRepository
                .findByParticipanteIdAndEventoIdComEvento(participante.getId(), eventoId);
        if (existente.isPresent()) {
            Inscricao inscricao = existente.get();
            if (inscricao.getStatus() == StatusInscricao.ATIVO || inscricao.getStatus() == StatusInscricao.UTILIZADO) {
                throw new NegocioException("Este usuário já está aprovado neste evento.");
            }
            if (inscricao.getStatus() == StatusInscricao.CANCELADO) {
                throw new NegocioException("Este usuário cancelou a própria inscrição neste evento.");
            }
            aprovarParticipacao(eventoId, inscricao.getId());
            return;
        }
        if (evento.getVagasRestantes() == null) {
            evento.setVagasRestantes(evento.getVagasTotais() != null ? evento.getVagasTotais() : 100);
        }
        if (evento.getVagasRestantes() <= 0) {
            throw new NegocioException("Não há vagas disponíveis para autorizar mais participantes.");
        }
        evento.setVagasRestantes(evento.getVagasRestantes() - 1);
        eventoRepository.save(evento);
        Inscricao inscricao = Inscricao.builder()
                .participante(participante)
                .evento(evento)
                .status(StatusInscricao.ATIVO)
                .build();
        inscricaoRepository.save(inscricao);
>>>>>>> 9f8190587fb70577392436388ea40a313988b188
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

        if (inscricao.getStatus() == StatusInscricao.PENDENTE) {
            throw new NegocioException("Check-in negado: a solicitação de participação ainda aguarda aprovação do organizador.");
        }

        if (inscricao.getStatus() == StatusInscricao.RECUSADO) {
            throw new NegocioException("Check-in negado: a solicitação de participação foi recusada pelo organizador.");
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

    /** Busca a inscrição de um participante em um evento, se existir. */
    @Transactional(readOnly = true)
    public Optional<Inscricao> buscarDoParticipanteNoEvento(Long participanteId, Long eventoId) {
        if (participanteId == null || eventoId == null) {
            return Optional.empty();
        }
        return inscricaoRepository.findByParticipanteIdAndEventoIdComEvento(participanteId, eventoId);
    }
    public List<Inscricao> buscarCertificadosDoUsuario(Long usuarioId) {
        return inscricaoRepository.findByParticipanteIdAndStatus(usuarioId, StatusInscricao.UTILIZADO);
    }
    @Transactional
    public void confirmarPresenca(Long inscricaoId) {
        Inscricao inscricao = inscricaoRepository.findById(inscricaoId)
                .orElseThrow(() -> new NegocioException("Inscrição não encontrada no sistema."));

        if (inscricao.getStatus() == StatusInscricao.CANCELADO
                || inscricao.getStatus() == StatusInscricao.PENDENTE
                || inscricao.getStatus() == StatusInscricao.RECUSADO) {
            throw new NegocioException("Só é possível confirmar presença de participantes aprovados.");
        }
        if (inscricao.getStatus() == StatusInscricao.PENDENTE) {
            throw new NegocioException("Aprove a solicitação de participação antes de confirmar a presença.");
        }
        if (inscricao.getStatus() == StatusInscricao.RECUSADO) {
            throw new NegocioException("Não é possível confirmar presença em uma solicitação recusada.");
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

    @Transactional
    public void configurarFrequenciaSorteio(Long eventoId, Integer percentual) {
        if (percentual == null || percentual < 0 || percentual > 100) {
            throw new NegocioException("Informe um percentual entre 0 e 100.");
        }
        Evento evento = eventoRepository.findByIdForUpdate(eventoId)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));
        evento.setSorteioFrequenciaMinima(percentual);
        eventoRepository.save(evento);
    }

    @Transactional(readOnly = true)
    public List<SorteioResultado> listarResultadosSorteio(Long eventoId) {
        return sorteioResultadoRepository.findByEventoIdOrderByRealizadoEmDescIdDesc(eventoId);
    }

    @Transactional
    public List<SorteioResultado> realizarSorteio(Long eventoId, Integer quantidade) {
        Evento evento = eventoRepository.findByIdForUpdate(eventoId)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));
        if (quantidade == null || quantidade < 1 || quantidade > 1000) {
            throw new NegocioException("Escolha uma quantidade entre 1 e 1000 pessoas.");
        }
        int minimo = evento.getSorteioFrequenciaMinima() == null ? 75 : evento.getSorteioFrequenciaMinima();
        List<InscritoFrequenciaDto> elegiveis = new ArrayList<>(listarInscritosComFrequencia(eventoId).stream()
                .filter(i -> !i.isInscricaoCancelada() && !i.isSolicitacaoPendente() && !i.isInscricaoRecusada())
                .filter(i -> i.getPercentualFrequencia() != null && i.getPercentualFrequencia() >= minimo)
                .toList());
        if (elegiveis.isEmpty()) {
            throw new NegocioException("Não há participantes elegíveis. Confira o percentual mínimo e as presenças registradas.");
        }
        if (quantidade > elegiveis.size()) {
            throw new NegocioException("A quantidade de pessoas sorteadas não pode superar os " + elegiveis.size() + " participantes elegíveis.");
        }
        java.util.Collections.shuffle(elegiveis, RANDOM);
        int rodada = (evento.getSorteioNumeroRodada() == null ? 0 : evento.getSorteioNumeroRodada()) + 1;
        LocalDateTime realizadoEm = LocalDateTime.now();
        List<SorteioResultado> resultados = new ArrayList<>();
        for (InscritoFrequenciaDto participante : elegiveis.subList(0, quantidade)) {
            SorteioResultado resultado = new SorteioResultado();
            resultado.setEvento(evento);
            resultado.setRodada(rodada);
            resultado.setNomeVencedor(participante.getNomeParticipante());
            resultado.setEmailVencedor(participante.getEmailParticipante());
            resultado.setPercentualFrequencia(participante.getPercentualFrequencia());
            resultado.setRealizadoEm(realizadoEm);
            resultados.add(resultado);
        }
        evento.setSorteioNumeroRodada(rodada);
        InscritoFrequenciaDto vencedor = elegiveis.get(0);
        evento.setSorteioVencedorNome(vencedor.getNomeParticipante());
        evento.setSorteioVencedorEmail(vencedor.getEmailParticipante());
        evento.setSorteioRealizadoEm(realizadoEm);
        eventoRepository.save(evento);
        return sorteioResultadoRepository.saveAll(resultados);
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
                .solicitacaoPendente(status == StatusInscricao.PENDENTE)
                .inscricaoRecusada(status == StatusInscricao.RECUSADO)
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
<<<<<<< HEAD
        if (inscricao.getStatus() == StatusInscricao.PENDENTE) {
            throw new NegocioException("Aprove a solicitação de participação antes de validar a presença.");
        }
        if (inscricao.getStatus() == StatusInscricao.RECUSADO) {
            throw new NegocioException("Não é possível validar uma solicitação recusada.");
=======
        if (inscricao.getStatus() == StatusInscricao.PENDENTE || inscricao.getStatus() == StatusInscricao.RECUSADO) {
            throw new NegocioException("Aprove a solicitação do participante antes de validar a participação.");
>>>>>>> 9f8190587fb70577392436388ea40a313988b188
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

    /**
     * Valida em lote somente as inscrições selecionadas. Todas precisam existir,
     * pertencer ao evento informado e não estar canceladas; a validação completa
     * acontece antes de qualquer alteração, para não liberar certificados
     * parcialmente quando o lote contém um item inválido.
     */
    @Transactional
    public int validarParticipacaoEmLote(Long eventoId, List<Long> inscricaoIds) {
        if (eventoId == null) {
            throw new NegocioException("Evento não informado.");
        }
        if (inscricaoIds == null || inscricaoIds.isEmpty()) {
            throw new NegocioException("Selecione ao menos um participante para validar.");
        }

        List<Long> ids = inscricaoIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            throw new NegocioException("Selecione ao menos um participante para validar.");
        }
        if (ids.size() > LIMITE_VALIDACOES_POR_LOTE) {
            throw new NegocioException("O lote de validação excede o limite permitido.");
        }

        List<Inscricao> selecionadas = inscricaoRepository.findAllById(ids);
        if (selecionadas.size() != ids.size()) {
            throw new NegocioException("Uma ou mais inscrições selecionadas não foram encontradas.");
        }

        List<Inscricao> paraAtualizar = new ArrayList<>();
        for (Inscricao inscricao : selecionadas) {
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
<<<<<<< HEAD
            if (inscricao.getStatus() == StatusInscricao.PENDENTE) {
                throw new NegocioException("Aprove as solicitações de participação pendentes antes de validar a presença.");
            }
            if (inscricao.getStatus() == StatusInscricao.RECUSADO) {
                throw new NegocioException("Não é possível validar uma solicitação recusada.");
=======
            if (inscricao.getStatus() == StatusInscricao.PENDENTE || inscricao.getStatus() == StatusInscricao.RECUSADO) {
                throw new NegocioException("Não é possível validar participantes com solicitação pendente ou recusada.");
>>>>>>> 9f8190587fb70577392436388ea40a313988b188
            }
            if (inscricao.getStatus() != StatusInscricao.UTILIZADO) {
                paraAtualizar.add(inscricao);
            }
        }

        LocalDateTime agora = LocalDateTime.now();
        for (Inscricao inscricao : paraAtualizar) {
            inscricao.setStatus(StatusInscricao.UTILIZADO);
            if (inscricao.getDataCheckIn() == null) {
                inscricao.setDataCheckIn(agora);
            }
        }
        if (!paraAtualizar.isEmpty()) {
            inscricaoRepository.saveAll(paraAtualizar);
        }
        return paraAtualizar.size();
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
<<<<<<< HEAD
                        && inscricao.getStatus() != StatusInscricao.CANCELADO
                        && inscricao.getStatus() != StatusInscricao.PENDENTE
                        && inscricao.getStatus() != StatusInscricao.RECUSADO
                        && inscricao.getStatus() != StatusInscricao.UTILIZADO)
=======
                        && inscricao.getStatus() == StatusInscricao.ATIVO)
>>>>>>> 9f8190587fb70577392436388ea40a313988b188
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
<<<<<<< HEAD
            // Solicitações pendentes ou recusadas não participam do diário de classe.
            if (inscricao.getStatus() == StatusInscricao.PENDENTE
                    || inscricao.getStatus() == StatusInscricao.RECUSADO
                    || inscricao.getStatus() == StatusInscricao.CANCELADO) {
=======
            if (inscricao.getStatus() == StatusInscricao.CANCELADO
                    || inscricao.getStatus() == StatusInscricao.PENDENTE
                    || inscricao.getStatus() == StatusInscricao.RECUSADO) {
>>>>>>> 9f8190587fb70577392436388ea40a313988b188
                return;
            }
            if (idsPresentes != null && idsPresentes.contains(inscricao.getId())) {

                // SE FOI SELECIONADO: Altera o status para UTILIZADO (Presença Confirmada)
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
            if (inscricao.getStatus() == StatusInscricao.CANCELADO
                    || inscricao.getStatus() == StatusInscricao.PENDENTE
                    || inscricao.getStatus() == StatusInscricao.RECUSADO) {
                throw new NegocioException("Só é possível confirmar presença em uma inscrição aprovada.");
            }
            if (inscricao.getStatus() == StatusInscricao.PENDENTE) {
                throw new NegocioException("Aprove a solicitação de participação antes de confirmar a presença.");
            }
            if (inscricao.getStatus() == StatusInscricao.RECUSADO) {
                throw new NegocioException("Não é possível confirmar presença em uma solicitação recusada.");
            }
            inscricao.setStatus(StatusInscricao.UTILIZADO);
            inscricao.setDataCheckIn(java.time.LocalDateTime.now());
        }
        inscricaoRepository.save(inscricao);
    }
}
