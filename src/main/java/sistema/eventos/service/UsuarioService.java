package sistema.eventos.service;

import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema.eventos.dto.RequestSolicitacaoOrganizadorDto;
import sistema.eventos.dto.RequestUsuarioDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.SolicitacaoOrganizador;
import sistema.eventos.model.StatusSolicitacao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.SolicitacaoRepository;
import sistema.eventos.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@AllArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final SolicitacaoRepository solicitacaoRepository;
    private final EmailService emailService;


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
    @Transactional
    public void solicitarAcessoOrganizador(String email, RequestSolicitacaoOrganizadorDto dto) {
        Usuario usuario = buscarPorEmail(email);
        if (usuario.getPerfil() != Perfil.ROLE_PARTICIPANTE) {
            throw new NegocioException("Esta conta já possui acesso de organizador ou administrador.");
        }

        var solicitacaoExistente = solicitacaoRepository.findByUsuarioId(usuario.getId());
        if (solicitacaoExistente.isPresent()
                && solicitacaoExistente.get().getStatus() == StatusSolicitacao.PENDENTE) {
            throw new NegocioException("Sua solicitação já está aguardando aprovação da administração.");
        }

        SolicitacaoOrganizador solicitacao = solicitacaoExistente.orElseGet(() -> {
            SolicitacaoOrganizador nova = new SolicitacaoOrganizador();
            nova.setUsuario(usuario);
            return nova;
        });
        solicitacao.setNomeCompleto(dto.getNomeCompleto().trim());
        solicitacao.setMatricula(dto.getMatricula().trim());
        solicitacao.setEmailInstitucional(dto.getEmailInstitucional().trim().toLowerCase(Locale.ROOT));
        solicitacao.setJustificativa(dto.getJustificativa().trim());
        solicitacao.setStatus(StatusSolicitacao.PENDENTE);
        solicitacao.setDataSolicitacao(LocalDateTime.now());
        solicitacaoRepository.save(solicitacao);
        emailService.enviarSolicitacaoParaAdmin(usuario);
    }
}
