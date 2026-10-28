package sistema.eventos.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.UsuarioRepository;
import sistema.eventos.service.FileStorageService;

/**
 * Disponibiliza o usuário autenticado e a URL da sua foto de perfil para os
 * fragmentos compartilhados de cabeçalho/menu.
 *
 * <p>As dependências são resolvidas de forma opcional para não quebrar testes
 * de fatia web que não carregam repositório nem serviço de armazenamento.</p>
 */
@ControllerAdvice
public class UsuarioAvatarAdvice {

    private final ObjectProvider<UsuarioRepository> usuarioRepository;
    private final ObjectProvider<FileStorageService> fileStorageService;

    public UsuarioAvatarAdvice(ObjectProvider<UsuarioRepository> usuarioRepository,
                               ObjectProvider<FileStorageService> fileStorageService) {
        this.usuarioRepository = usuarioRepository;
        this.fileStorageService = fileStorageService;
    }

    @ModelAttribute
    public void adicionarDadosDoUsuarioLogado(Model model, Authentication authentication) {
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return;
        }

        UsuarioRepository repository = usuarioRepository.getIfAvailable();
        FileStorageService storage = fileStorageService.getIfAvailable();
        if (repository == null || storage == null) {
            return;
        }

        Usuario usuario = repository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (usuario == null) {
            return;
        }

        model.addAttribute("usuarioLogado", usuario);
        model.addAttribute("fotoPerfilUrl", storage.publicImageUrl(usuario.getFotoPerfil()));
    }
}
