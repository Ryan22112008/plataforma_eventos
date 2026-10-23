package sistema.eventos.service;

import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import sistema.eventos.dto.HistoricoEventosOrganizadosDto;
import sistema.eventos.dto.HistoricoParticipacaoDto;
import sistema.eventos.dto.RequestSolicitacaoOrganizadorDto;
import sistema.eventos.dto.RequestUsuarioDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.SolicitacaoOrganizador;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.StatusSolicitacao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.repository.SolicitacaoRepository;
import sistema.eventos.repository.UsuarioRepository;
import sistema.eventos.service.PresencaService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@AllArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final SolicitacaoRepository solicitacaoRepository;
    private final EmailService emailService;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final PresencaService presencaService;


    /**
     * Realiza o cadastro de novos usuários na plataforma de forma dinâmica.
     * Valida senhas, duplicidade de e-mail e mapeia o perfil selecionado.
     */
    @Transactional
    public void cadastrar(RequestUsuarioDto dto) {
        if (dto.getSenha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new NegocioException("A senha não pode exceder 72 bytes UTF-8.");
        }
        // 1. Validação de correspondência de senha (Excelente!)
        if (!dto.getSenha().equals(dto.getConfirmacaoSenha())) {
            throw new NegocioException("A confirmação de senha não coincide com a senha digitada.");
        }

        // 2. Fail-Fast: Evita processamento se o e-mail já existir
        String email = dto.getEmail().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new NegocioException("Este e-mail já está cadastrado no sistema.");
        }

        Usuario novoUsuario = Usuario.builder()
                .nome(dto.getNome().trim())
                .email(email)
                .senha(passwordEncoder.encode(dto.getSenha()))
                .perfil(Perfil.ROLE_PARTICIPANTE)
                .ativo(true)
                .build();

        usuarioRepository.save(novoUsuario);
    }

    /**
     * Busca um usuário pelo ID. Retorna erro de domínio caso não exista.
     */
    @Transactional(readOnly = true)
    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Usuário com ID " + id + " não encontrado."));
    }

    /**
     * Busca um usuário pelo e-mail. Essencial para autenticação e amarração de contextos.
     */
    @Transactional(readOnly = true)
    public Usuario buscarPorEmail(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return usuarioRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new NegocioException("Usuário com e-mail " + email + " não encontrado."));
    }
    @Transactional
    public Usuario salvar(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    /**
     * Lista usuários com paginação e filtros opcionais para a área do Super Admin.
     *
     * @param nome   filtro parcial por nome (ignorado se nulo/vazio)
     * @param email  filtro parcial por e-mail (ignorado se nulo/vazio)
     * @param perfil filtro exato por perfil (ignorado se null)
     * @param ativo  filtro exato por status ativo/inativo (ignorado se null)
     * @param pageable paginação e ordenação
     * @return página de usuários já filtrada e ordenada
     */
    @Transactional(readOnly = true)
    public Page<Usuario> listarUsuarios(
            String nome, String email, Perfil perfil, Boolean ativo, Pageable pageable) {
        return usuarioRepository.findFiltros(nome, email, perfil, ativo, pageable);
    }

    /**
     * Conta usuários por perfil para exibição no resumo da página.
     */
    @Transactional(readOnly = true)
    public long contarPorPerfil(Perfil perfil) {
        return usuarioRepository.countByPerfil(perfil);
    }

    /**
     * Conta usuários por status ativo/inativo para exibição no resumo da página.
     */
    @Transactional(readOnly = true)
    public long contarPorAtivo(boolean ativo) {
        return usuarioRepository.countByAtivo(ativo);
    }
    /**
     * Desativa um usuário (ativo = false).
     * Não exclui o registro do banco; preserva histórico e relacionamentos.
     *
     * @param id              ID do usuário a ser desativado
     * @param idSolicitante   ID do SUPER_ADMIN que está executando a ação
     * @throws NegocioException se o usuário não existir, já estiver inativo, ou for o próprio solicitante
     */
    @Transactional
    public void desativarUsuario(Long id, Long idSolicitante) {
        if (id.equals(idSolicitante)) {
            throw new NegocioException("Você não pode desativar a própria conta.");
        }

        Usuario usuario = buscarPorId(id);
        if (!usuario.isAtivo()) {
            throw new NegocioException("Este usuário já está inativo.");
        }

        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
    }

    /**
     * Reativa um usuário inativo (ativo = true).
     * Preserva perfil e dados existentes.
     *
     * @param id ID do usuário a ser reativado
     * @throws NegocioException se o usuário não existir ou já estiver ativo
     */
    @Transactional
    public void reativarUsuario(Long id) {
        Usuario usuario = buscarPorId(id);
        if (usuario.isAtivo()) {
            throw new NegocioException("Este usuário já está ativo.");
        }

        usuario.setAtivo(true);
        usuarioRepository.save(usuario);
    }

    /**
     * Remove o acesso de organizador, alterando o perfil de ROLE_ORGANIZADOR para ROLE_PARTICIPANTE.
     * Não exclui o usuário nem apaga eventos, inscrições ou qualquer relacionamento.
     *
     * @param id              ID do usuário a ter o acesso de organizador removido
     * @param idSolicitante   ID do SUPER_ADMIN que está executando a ação
     * @throws NegocioException se o usuário não existir, não for ORGANIZADOR, ou for o próprio solicitante
     */
    @Transactional
    public void removerAcessoOrganizador(Long id, Long idSolicitante) {
        if (id.equals(idSolicitante)) {
            throw new NegocioException("Você não pode alterar o próprio perfil.");
        }

        Usuario usuario = buscarPorId(id);
        if (usuario.getPerfil() != Perfil.ROLE_ORGANIZADOR) {
            throw new NegocioException("Este usuário não possui perfil de organizador.");
        }

        usuario.setPerfil(Perfil.ROLE_PARTICIPANTE);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void solicitarAcessoOrganizador(String email, RequestSolicitacaoOrganizadorDto dto) {
        Usuario usuario = buscarPorEmail(email);
        if (usuario.getPerfil() != Perfil.ROLE_PARTICIPANTE) {
            throw new NegocioException("Esta conta já possui acesso de organizador ou administrador.");
        }

        var solicitacaoExistente = solicitacaoRepository.findByUsuarioId(usuario.getId());

        // Regra 2: Se existe solicitação PENDENTE, bloqueia nova solicitação
        if (solicitacaoExistente.isPresent()
                && solicitacaoExistente.get().getStatus() == StatusSolicitacao.PENDENTE) {
            throw new NegocioException("Sua solicitação já está aguardando aprovação da administração.");
        }

        SolicitacaoOrganizador solicitacao;

        // Regra 3: Se existe solicitação REJEITADA, reutiliza o mesmo registro
        if (solicitacaoExistente.isPresent()
                && solicitacaoExistente.get().getStatus() == StatusSolicitacao.REJEITADO) {
            solicitacao = solicitacaoExistente.get();
            solicitacao.setNomeCompleto(dto.getNomeCompleto().trim());
            solicitacao.setMatricula(dto.getMatricula().trim());
            solicitacao.setEmailInstitucional(dto.getEmailInstitucional().trim().toLowerCase(Locale.ROOT));
            solicitacao.setJustificativa(dto.getJustificativa().trim());
            solicitacao.setOrgao(dto.getOrgao() != null ? dto.getOrgao().trim() : null);
            solicitacao.setStatus(StatusSolicitacao.PENDENTE);
            solicitacao.setDataSolicitacao(LocalDateTime.now());
        }
        // Regra 4: Se existe solicitação APROVADA, verifica perfil do usuário
        else if (solicitacaoExistente.isPresent()
                && solicitacaoExistente.get().getStatus() == StatusSolicitacao.APROVADO) {
            // Se usuário já tem perfil ORGANIZADOR, não permite nova solicitação
            if (usuario.getPerfil() == Perfil.ROLE_ORGANIZADOR) {
                throw new NegocioException("Este usuário já possui acesso de organizador aprovado.");
            }
            // Se ainda for PARTICIPANTE (dado legado), reutiliza o registro aprovado
            // e atualiza para nova solicitação PENDENTE
            solicitacao = solicitacaoExistente.get();
            solicitacao.setNomeCompleto(dto.getNomeCompleto().trim());
            solicitacao.setMatricula(dto.getMatricula().trim());
            solicitacao.setEmailInstitucional(dto.getEmailInstitucional().trim().toLowerCase(Locale.ROOT));
            solicitacao.setJustificativa(dto.getJustificativa().trim());
            solicitacao.setOrgao(dto.getOrgao() != null ? dto.getOrgao().trim() : null);
            solicitacao.setStatus(StatusSolicitacao.PENDENTE);
            solicitacao.setDataSolicitacao(LocalDateTime.now());
        }
        // Regra 1: Se não existe solicitação, cria uma nova
        else {
            solicitacao = new SolicitacaoOrganizador();
            solicitacao.setUsuario(usuario);
        }

        solicitacao.setNomeCompleto(dto.getNomeCompleto().trim());
        solicitacao.setMatricula(dto.getMatricula().trim());
        solicitacao.setEmailInstitucional(dto.getEmailInstitucional().trim().toLowerCase(Locale.ROOT));
        solicitacao.setJustificativa(dto.getJustificativa().trim());
        solicitacao.setOrgao(dto.getOrgao() != null ? dto.getOrgao().trim() : null);
        solicitacao.setStatus(StatusSolicitacao.PENDENTE);
        solicitacao.setDataSolicitacao(LocalDateTime.now());
        solicitacaoRepository.save(solicitacao);
        emailService.enviarSolicitacaoParaAdmin(usuario);
    }

    /**
     * Lista o histórico de participação de um usuário no painel SUPER_ADMIN.
     * Considera apenas inscrições com status UTILIZADO (participação efetiva
     * confirmada por check-in/consolidação de presença).
     *
     * @param usuarioId ID do usuário participante
     * @return lista ordenada do mais recente para o mais antigo
     */
    @Transactional(readOnly = true)
    public List<HistoricoParticipacaoDto> listarHistoricoParticipacao(Long usuarioId) {
        List<Inscricao> inscricoes = inscricaoRepository.findByParticipanteIdComEvento(usuarioId);

        return inscricoes.stream()
                .filter(inscricao -> inscricao.getStatus() == StatusInscricao.UTILIZADO)
                .map(inscricao -> {
                    Evento evento = inscricao.getEvento();
                    Double frequencia = null;
                    try {
                        frequencia = presencaService.calcularFrequencia(usuarioId, evento.getId());
                    } catch (Exception ignored) {
                        // Frequência não aplicável (ex: evento sem sessões)
                    }

                    boolean certificadoDisponivel = certificadoDisponivelPara(inscricao);

                    return HistoricoParticipacaoDto.builder()
                            .inscricaoId(inscricao.getId())
                            .eventoId(evento.getId())
                            .tituloEvento(evento.getTitulo())
                            .dataInicio(evento.getDataInicio())
                            .dataTermino(evento.getDataTermino())
                            .modalidade(evento.getModalidade() != null ? evento.getModalidade().name() : null)
                            .statusParticipacao("Presente")
                            .percentualFrequencia(frequencia)
                            .certificadoDisponivel(certificadoDisponivel)
                            .build();
                })
                .sorted((a, b) -> {
                    java.time.LocalDate d1 = a.getDataInicio() != null ? a.getDataInicio() : java.time.LocalDate.MIN;
                    java.time.LocalDate d2 = b.getDataInicio() != null ? b.getDataInicio() : java.time.LocalDate.MIN;
                    return d2.compareTo(d1);
                })
                .toList();
    }

    /**
     * Lista os eventos organizados por um usuário no painel SUPER_ADMIN.
     * Considera a associação real entre Evento.organizador_id e Usuario.id,
     * independentemente do perfil atual do usuário ou do status do evento.
     * Não acessa evento.getOrganizador() para evitar LazyInitializationException.
     *
     * @param usuarioId ID do usuário organizador
     * @return lista ordenada do mais recente para o mais antigo; vazia se não houver eventos
     */
    @Transactional(readOnly = true)
    public List<HistoricoEventosOrganizadosDto> listarEventosOrganizados(Long usuarioId) {
        List<Evento> eventos = eventoRepository.findByOrganizadorId(usuarioId);

        return eventos.stream()
                .map(evento -> HistoricoEventosOrganizadosDto.builder()
                        .eventoId(evento.getId())
                        .titulo(evento.getTitulo())
                        .dataInicio(evento.getDataInicio())
                        .dataTermino(evento.getDataTermino())
                        .modalidade(evento.getModalidade() != null ? evento.getModalidade().name() : null)
                        .status(evento.getStatus() != null ? evento.getStatus().name() : null)
                        .nicho(evento.getNicho() != null ? evento.getNicho().name() : null)
                        .cargaHoraria(evento.getCargaHoraria())
                        .totalInscritos(inscricaoRepository.contarInscricoesValidasDoEvento(evento.getId()))
                        .build())
                .sorted((a, b) -> {
                    LocalDate d1 = a.getDataInicio() != null ? a.getDataInicio() : LocalDate.MIN;
                    LocalDate d2 = b.getDataInicio() != null ? b.getDataInicio() : LocalDate.MIN;
                    return d2.compareTo(d1);
                })
                .toList();
    }

    /**
     * Reutiliza a mesma regra de elegibilidade do CertificadoService para
     * determinar se o certificado está disponível para uma inscrição.
     * Não duplica a lógica de geração de certificado.
     */
    private boolean certificadoDisponivelPara(Inscricao inscricao) {
        if (inscricao.getStatus() != StatusInscricao.UTILIZADO) {
            return false;
        }
        Evento evento = inscricao.getEvento();
        String frente = evento.getCertificadoTemplate();
        String verso = evento.getCertificadoTemplateVerso();
        return frente != null && !frente.trim().isEmpty()
                && verso != null && !verso.trim().isEmpty();
    }
}
