package sistema.eventos.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Usuario;
import sistema.eventos.service.FileStorageService;
import sistema.eventos.service.PerfilService;
import sistema.eventos.service.UsuarioService;

@Controller
@RequestMapping("/perfil")
@RequiredArgsConstructor
public class PerfilController {

    private final UsuarioService usuarioService;
    private final PerfilService perfilService;
    private final FileStorageService fileStorageService;

    @GetMapping
    public String exibirPerfil(Authentication authentication, Model model) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        model.addAttribute("usuario", usuario);
        model.addAttribute("fotoPerfilUrl", fileStorageService.publicImageUrl(usuario.getFotoPerfil()));
        return "perfil";
    }

    @PostMapping("/foto")
    public String enviarFoto(@RequestParam("foto") MultipartFile foto,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            perfilService.atualizarFoto(authentication.getName(), foto);
            redirectAttributes.addFlashAttribute("sucessoPerfil", "Foto de perfil atualizada com sucesso.");
        } catch (NegocioException e) {
            redirectAttributes.addFlashAttribute("erroPerfil", e.getMessage());
        }
        return "redirect:/perfil";
    }

    @PostMapping("/foto/remover")
    public String removerFoto(Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            perfilService.removerFoto(authentication.getName());
            redirectAttributes.addFlashAttribute("sucessoPerfil", "Foto de perfil removida. O avatar padrão voltou a ser exibido.");
        } catch (NegocioException e) {
            redirectAttributes.addFlashAttribute("erroPerfil", e.getMessage());
        }
        return "redirect:/perfil";
    }
}
