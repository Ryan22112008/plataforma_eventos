package sistema.eventos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema.eventos.model.*;
import sistema.eventos.repository.*;
import sistema.eventos.exception.NegocioException;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresencaService {

    private final PresencaRepository presencaRepository;
    private final InscricaoRepository inscricaoRepository;
    private final SessaoRepository sessaoRepository;
    private final UsuarioRepository usuarioRepository; // Assumindo que você tem este repositório

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

    private void validarAcessoAoEvento(Evento evento, String login) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(login)
                .orElseThrow(() -> new SecurityException("Usuário não autorizado."));
        if (usuario.getPerfil() != Perfil.ROLE_ADMIN
                && (evento.getOrganizador() == null
                || !evento.getOrganizador().getEmail().equalsIgnoreCase(usuario.getEmail()))) {
            throw new org.springframework.security.access.AccessDeniedException("Você não tem acesso a este evento.");
        }
    }
}
