package sistema.eventos.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema.eventos.dto.AvaliacaoEventoRequestDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Usuario;
import sistema.eventos.service.AvaliacaoEventoService;
import sistema.eventos.service.UsuarioService;

@Controller
@RequestMapping("/eventos/{eventoId}/avaliacoes")
@AllArgsConstructor
public class AvaliacaoEventoController {

    private final AvaliacaoEventoService avaliacaoService;
    private final UsuarioService usuarioService;

    @PostMapping
    public String criar(@PathVariable Long eventoId,
                        Authentication authentication,
                        @Valid @ModelAttribute("avaliacaoDto") AvaliacaoEventoRequestDto dto,
                        BindingResult result,
                        RedirectAttributes attributes) {
        Usuario usuario = usuarioLogadoOuErro(authentication, attributes);
        if (usuario == null) {
            return "redirect:/eventos/" + eventoId;
        }
        if (result.hasErrors()) {
            attributes.addFlashAttribute("erroAvaliacao", "Preencha a nota e o coment\u00e1rio corretamente.");
            return "redirect:/eventos/" + eventoId;
        }
        try {
            avaliacaoService.criar(eventoId, usuario, dto);
            attributes.addFlashAttribute("sucessoAvaliacao", "Sua avalia\u00e7\u00e3o foi registrada com sucesso!");
        } catch (NegocioException | AccessDeniedException e) {
            attributes.addFlashAttribute("erroAvaliacao", e.getMessage());
        }
        return "redirect:/eventos/" + eventoId;
    }

    @PostMapping("/editar")
    public String editar(@PathVariable Long eventoId,
                         Authentication authentication,
                         @Valid @ModelAttribute("avaliacaoDto") AvaliacaoEventoRequestDto dto,
                         BindingResult result,
                         RedirectAttributes attributes) {
        Usuario usuario = usuarioLogadoOuErro(authentication, attributes);
        if (usuario == null) {
            return "redirect:/eventos/" + eventoId;
        }
        if (result.hasErrors()) {
            attributes.addFlashAttribute("erroAvaliacao", "Preencha a nota e o coment\u00e1rio corretamente.");
            return "redirect:/eventos/" + eventoId;
        }
        try {
            avaliacaoService.atualizar(eventoId, usuario, dto);
            attributes.addFlashAttribute("sucessoAvaliacao", "Sua avalia\u00e7\u00e3o foi atualizada com sucesso!");
        } catch (NegocioException | AccessDeniedException e) {
            attributes.addFlashAttribute("erroAvaliacao", e.getMessage());
        }
        return "redirect:/eventos/" + eventoId;
    }

    @PostMapping("/excluir")
    public String excluir(@PathVariable Long eventoId,
                          Authentication authentication,
                          RedirectAttributes attributes) {
        Usuario usuario = usuarioLogadoOuErro(authentication, attributes);
        if (usuario == null) {
            return "redirect:/eventos/" + eventoId;
        }
        try {
            avaliacaoService.excluir(eventoId, usuario);
            attributes.addFlashAttribute("sucessoAvaliacao", "Sua avalia\u00e7\u00e3o foi exclu\u00edda com sucesso!");
        } catch (NegocioException | AccessDeniedException e) {
            attributes.addFlashAttribute("erroAvaliacao", e.getMessage());
        }
        return "redirect:/eventos/" + eventoId;
    }

    private Usuario usuarioLogadoOuErro(Authentication authentication, RedirectAttributes attributes) {
        UserDetails userDetails = usuarioLogado(authentication);
        if (userDetails == null) {
            attributes.addFlashAttribute("erroAvaliacao", "Voc\u00ea precisa estar autenticado para avaliar o evento.");
            return null;
        }
        return usuarioService.buscarPorEmail(userDetails.getUsername());
    }

    private UserDetails usuarioLogado(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserDetails)) {
            return null;
        }
        return (UserDetails) authentication.getPrincipal();
    }
}
