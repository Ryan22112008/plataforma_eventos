package sistema.eventos.service;

import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema.eventos.dto.CadastroUsuarioDto;
import sistema.eventos.dto.RequestUsuarioDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.SolicitacaoOrganizador;
import sistema.eventos.model.StatusSolicitacao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.SolicitacaoRepository;
import sistema.eventos.repository.UsuarioRepository;

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
        // 1. Validação de correspondência de senha (Excelente!)
        if (!dto.getSenha().equals(dto.getConfirmacaoSenha())) {
            throw new NegocioException("A confirmação de senha não coincide com a senha digitada.");
        }

        // 2. Fail-Fast: Evita processamento se o e-mail já existir
        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new NegocioException("Este e-mail já está cadastrado no sistema.");
        }

        // 🔒 ALTERAÇÃO CRÍTICA DE SEGURANÇA: Todo usuário nasce como PARTICIPANTE.
        // Ele não ganha o cargo de Organizador direto de forma autônoma.
        Perfil perfilInicial = Perfil.ROLE_PARTICIPANTE;

        Usuario novoUsuario = Usuario.builder()
                .nome(dto.getNome())
                .email(dto.getEmail())
                .senha(passwordEncoder.encode(dto.getSenha()))
                .perfil(perfilInicial) // Força o perfil básico de ouvinte/autor
                .ativo(true)
                .build();

        // Salva o usuário para persistir no banco e gerar o ID (obrigatório para a FK da solicitação)
        Usuario usuarioSalvo = usuarioRepository.save(novoUsuario);

        // 🎯 GATILHO INSTITUCIONAL: Se ele selecionou que quer ser Organizador...
        if ("ROLE_ORGANIZADOR".equalsIgnoreCase(dto.getRole())) {

            // Cria a solicitação pendente amarrada ao usuário que acabou de ser salvo
            SolicitacaoOrganizador solicitacao = new SolicitacaoOrganizador();
            solicitacao.setUsuario(usuarioSalvo);
            solicitacao.setStatus(StatusSolicitacao.PENDENTE);
            solicitacaoRepository.save(solicitacao);

            // Dispara a notificação por e-mail para a equipe de TI/Admin da SEPLAG
            emailService.enviarSolicitacaoParaAdmin(usuarioSalvo, solicitacao.getId());
        }
    }

    /**
     * Método idempotente para criação ou atualização de usuários mestres do sistema (carga inicial).
     */
    @Transactional
    public Usuario criarOuAtualizarUsuarioFixo(String nome, String email, String senhaPura, Perfil perfil) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseGet(Usuario::new);

        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(senhaPura));
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);

        return usuarioRepository.save(usuario);
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
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NegocioException("Usuário com e-mail " + email + " não encontrado."));
    }
    @Transactional
    public Usuario salvar(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }
    @Transactional
    public void cadastrarUsuario(CadastroUsuarioDto dto) {
        Usuario usuario = new Usuario();
        usuario.setNome(dto.getNome());
        usuario.setEmail(dto.getEmail());
        usuario.setSenha(passwordEncoder.encode(dto.getSenha()));

        // 🔒 Todo mundo nasce como ALuno/Participante comum por segurança
        usuario.setPerfil(Perfil.ROLE_PARTICIPANTE);
        usuarioRepository.save(usuario);

        // 🎯 Se ele marcou que quer ser professor, entra o fluxo de análise
        if (dto.isQuerSerProfessor()) {
            SolicitacaoOrganizador solicitacao = new SolicitacaoOrganizador();
            solicitacao.setUsuario(usuario);
            solicitacao.setStatus(StatusSolicitacao.PENDENTE);
            solicitacaoRepository.save(solicitacao);

            // Dispara o e-mail em background para o administrador
            emailService.enviarSolicitacaoParaAdmin(usuario, solicitacao.getId());
        }
    }
}