package sistema.eventos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema.eventos.dto.AlteracaoPresencaRequestDto;
import sistema.eventos.model.*;
import sistema.eventos.repository.*;
import sistema.eventos.exception.NegocioException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresencaService {

    /** Limite defensivo para evitar lotes arbitrariamente grandes vindos do navegador. */
    private static final int LIMITE_ALTERACOES_POR_LOTE = 1000;

    private final PresencaRepository presencaRepository;
    private final InscricaoRepository inscricaoRepository;
    private final SessaoRepository sessaoRepository;
    private final UsuarioRepository usuarioRepository; // Assumindo que você tem este repositório
    private final EventoRepository eventoRepository;

    @Transactional
    public void registrarPresenca(Long alunoId, String qrCodeToken) {
        log.info("Iniciando registro de presença para aluno ID: {}", alunoId);

        // 1. Busca sessão pelo Token
        Sessao sessao = sessaoRepository.findByQrCodeToken(qrCodeToken)
                .orElseThrow(() -> new IllegalArgumentException("QR Code inválido ou expirado."));

        // 2. Validação de temporalidade
        if (!sessao.isAtiva()) {
            throw new IllegalStateException("Esta sessão não está aberta para check-in.");
        }

        // 3. Validação de Inscrição (O aluno tem direito a estar aqui?)
        if (!inscricaoRepository.existsByParticipanteIdAndEventoIdAndStatusNot(
                alunoId, sessao.getEvento().getId(), StatusInscricao.CANCELADO)) {
            throw new SecurityException("Aluno não possui inscrição válida neste evento.");
        }

        // 4. Idempotência (Já marcou?)
        if (presencaRepository.existsByAlunoIdAndSessaoId(alunoId, sessao.getId())) {
            log.warn("Tentativa de registro duplicado para aluno {} na sessão {}", alunoId, sessao.getId());
            return;
        }

        // 5. Persistência
        Presenca novaPresenca = new Presenca();
        novaPresenca.setAluno(usuarioRepository.getReferenceById(alunoId));
        novaPresenca.setSessao(sessao);
        novaPresenca.setStatus(StatusPresenca.PRESENTE);
        novaPresenca.setDataRegistro(LocalDateTime.now());

        presencaRepository.save(novaPresenca);
        log.info("Presença registrada com sucesso para aluno ID: {}", alunoId);
    }

    @Transactional
    public void atualizarStatusPresenca(Long presencaId, StatusPresenca novoStatus, String professorLogin) {
        Presenca presenca = presencaRepository.findById(presencaId)
                .orElseThrow(() -> new IllegalArgumentException("Registro de presença não encontrado."));

        log.info("Professor {} alterando status da presença {} de {} para {}",
                professorLogin, presencaId, presenca.getStatus(), novoStatus);
        validarAcessoAoEvento(presenca.getSessao().getEvento(), professorLogin);

        presenca.setStatus(novoStatus);
        presenca.setAlteradoPor(professorLogin);
        presenca.setDataModificacao(LocalDateTime.now());

        presencaRepository.save(presenca);
    }

    @Transactional(readOnly = true)
    public double calcularFrequencia(Long alunoId, Long eventoId) {
        long totalSessoes = sessaoRepository.countByEventoId(eventoId);
        if (totalSessoes == 0) return 100.0;

        long totalPresencas = presencaRepository.countPresencasConfirmadasPorAluno(alunoId, eventoId);
        return ((double) totalPresencas / totalSessoes) * 100;
    }
    @Transactional
    public void atualizarStatusPresencaManual(Long alunoId, Long sessaoId, StatusPresenca novoStatus, String professorNome) {
        log.info("Professor {} alterando status manualmente para aluno {} na sessão {}", professorNome, alunoId, sessaoId);

        Sessao sessao = sessaoRepository.findById(sessaoId)
                .orElseThrow(() -> new NegocioException("Sessão não encontrada."));
        validarAcessoAoEvento(sessao.getEvento(), professorNome);

        // 1. Busca um registro existente ou cria um novo se não existir (o "upsert")
        Presenca presenca = presencaRepository.findByAlunoIdAndSessaoId(alunoId, sessaoId)
                .orElseGet(() -> {
                    // Se não existe, criamos uma nova instância
                    Presenca p = new Presenca();
                    p.setAluno(usuarioRepository.getReferenceById(alunoId));
                    p.setSessao(sessao);
                    return p;
                });

        // 2. Atualiza os dados
        presenca.setStatus(novoStatus);
        presenca.setAlteradoPor(professorNome);

        // O @PreUpdate na sua entidade cuidará da data de modificação automaticamente
        presencaRepository.save(presenca);

        log.info("Status da presença {} atualizado com sucesso.", presenca.getId());
    }

    /**
     * Salva, em uma única transação, todas as alterações de presença feitas na
     * grade. O evento vem da rota autenticada; cada sessão e cada participante
     * são conferidos contra esse evento antes de qualquer gravação, de modo que
     * um lote inválido não deixa alterações parciais.
     */
    @Transactional
    public int atualizarStatusPresencasEmLote(Long eventoId,
                                              List<AlteracaoPresencaRequestDto> alteracoes,
                                              String professorNome) {
        if (eventoId == null) {
            throw new NegocioException("Evento não informado.");
        }

        Evento evento = eventoRepository.findByIdComOrganizador(eventoId)
                .orElseThrow(() -> new NegocioException("Evento não encontrado."));
        validarAcessoAoEvento(evento, professorNome);

        if (alteracoes == null || alteracoes.isEmpty()) {
            throw new NegocioException("Nenhuma alteração de presença foi informada.");
        }
        if (alteracoes.size() > LIMITE_ALTERACOES_POR_LOTE) {
            throw new NegocioException("O lote de alterações excede o limite permitido.");
        }

        Set<String> chaves = new HashSet<>();
        List<Sessao> sessoes = new ArrayList<>(alteracoes.size());

        // Primeira passada: valida todo o lote sem alterar nenhuma entidade.
        for (AlteracaoPresencaRequestDto alteracao : alteracoes) {
            if (alteracao == null
                    || alteracao.getAlunoId() == null
                    || alteracao.getSessaoId() == null
                    || alteracao.getStatus() == null) {
                throw new NegocioException("Alteração de presença inválida.");
            }

            String chave = alteracao.getAlunoId() + "_" + alteracao.getSessaoId();
            if (!chaves.add(chave)) {
                throw new NegocioException("O lote contém alterações duplicadas para o mesmo participante e sessão.");
            }

            Sessao sessao = sessaoRepository.findById(alteracao.getSessaoId())
                    .orElseThrow(() -> new NegocioException("Sessão não encontrada para a alteração informada."));
            if (sessao.getEvento() == null || !eventoId.equals(sessao.getEvento().getId())) {
                throw new NegocioException("A sessão informada não pertence a este evento.");
            }

            if (!inscricaoRepository.existsByParticipanteIdAndEventoId(alteracao.getAlunoId(), eventoId)) {
                throw new NegocioException("O participante informado não pertence a este evento.");
            }

            sessoes.add(sessao);
        }

        // Segunda passada: aplica as alterações já validadas.
        List<Presenca> paraSalvar = new ArrayList<>(alteracoes.size());
        for (int i = 0; i < alteracoes.size(); i++) {
            AlteracaoPresencaRequestDto alteracao = alteracoes.get(i);
            Sessao sessao = sessoes.get(i);

            Presenca presenca = presencaRepository
                    .findByAlunoIdAndSessaoId(alteracao.getAlunoId(), alteracao.getSessaoId())
                    .orElseGet(() -> {
                        Presenca nova = new Presenca();
                        nova.setAluno(usuarioRepository.getReferenceById(alteracao.getAlunoId()));
                        nova.setSessao(sessao);
                        return nova;
                    });

            presenca.setStatus(alteracao.getStatus());
            presenca.setAlteradoPor(professorNome);
            paraSalvar.add(presenca);
        }

        presencaRepository.saveAll(paraSalvar);

        log.info("Professor {} salvou {} alterações de presença no evento {}.",
                professorNome, paraSalvar.size(), eventoId);
        return paraSalvar.size();
    }

    private void validarAcessoAoEvento(Evento evento, String login) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(login)
                .orElseThrow(() -> new SecurityException("Usuário não autorizado."));
        if (usuario.getPerfil() != Perfil.ROLE_SUPER_ADMIN
                && (evento.getOrganizador() == null
                || !evento.getOrganizador().getEmail().equalsIgnoreCase(usuario.getEmail()))) {
            throw new org.springframework.security.access.AccessDeniedException("Você não tem acesso a este evento.");
        }
    }
}
