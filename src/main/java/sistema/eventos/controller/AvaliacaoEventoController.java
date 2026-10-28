package sistema.eventos.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
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

import java.util.Objects;

@Controller
@RequiredArgsConstructor
@RequestMapping("/eventos/{eventoId}/avaliacoes")
public class AvaliacaoEventoController {

    private final AvaliacaoEventoService avaliacaoEventoService;
    private final UsuarioService usuarioService;

    @PostMapping
    public String criar(@PathVariable Long eventoId,
                        @AuthenticationPrincipal UserDetails userDetails,
                        @Valid @ModelAttribute("avaliacaoDto") AvaliacaoEventoRequestDto dto,
                        BindingResult result,
                        RedirectAttributes attributes) {
        Usuario usuario = usuarioAutenticado(userDetails, attributes);
        if (usuario == null) {
            return redirecionarParaEvento(eventoId);
        }
        if (result.hasErrors()) {
            attributes.addFlashAttribute("erroAvaliacao", primeiraMensagemDeErro(result));
            return redirecionarParaEvento(eventoId);
        }

        try {
            avaliacaoEventoService.criar(eventoId, usuario, dto);
            attributes.addFlashAttribute("sucessoAvaliacao", "Sua avaliação foi registrada com sucesso!");
        } catch (NegocioException ex) {
            attributes.addFlashAttribute("erroAvaliacao", ex.getMessage());
        }
        return redirecionarParaEvento(eventoId);
    }

    @PostMapping("/editar")
    public String editar(@PathVariable Long eventoId,
                         @AuthenticationPrincipal UserDetails userDetails,
                         @Valid @ModelAttribute("avaliacaoDto") AvaliacaoEventoRequestDto dto,
                         BindingResult result,
                         RedirectAttributes attributes) {
        Usuario usuario = usuarioAutenticado(userDetails, attributes);
        if (usuario == null) {
            return redirecionarParaEvento(eventoId);
        }
        if (result.hasErrors()) {
            attributes.addFlashAttribute("erroAvaliacao", primeiraMensagemDeErro(result));
            return redirecionarParaEvento(eventoId);
        }

        try {
            avaliacaoEventoService.atualizar(eventoId, usuario, dto);
            attributes.addFlashAttribute("sucessoAvaliacao", "Sua avaliação foi atualizada com sucesso!");
        } catch (NegocioException ex) {
            attributes.addFlashAttribute("erroAvaliacao", ex.getMessage());
        }
        return redirecionarParaEvento(eventoId);
    }

    @PostMapping("/excluir")
    public String excluir(@PathVariable Long eventoId,
                          @AuthenticationPrincipal UserDetails userDetails,
                          RedirectAttributes attributes) {
        Usuario usuario = usuarioAutenticado(userDetails, attributes);
        if (usuario == null) {
            return redirecionarParaEvento(eventoId);
        }

        try {
            avaliacaoEventoService.excluir(eventoId, usuario);
            attributes.addFlashAttribute("sucessoAvaliacao", "Sua avaliação foi excluída com sucesso!");
        } catch (NegocioException ex) {
            attributes.addFlashAttribute("erroAvaliacao", ex.getMessage());
        }
        return redirecionarParaEvento(eventoId);
    }

    private Usuario usuarioAutenticado(UserDetails userDetails, RedirectAttributes attributes) {
        if (userDetails == null) {
            attributes.addFlashAttribute("erroAvaliacao", "Você precisa estar autenticado para avaliar o evento.");
            return null;
        }
        try {
            return usuarioService.buscarPorEmail(userDetails.getUsername());
        } catch (NegocioException ex) {
            attributes.addFlashAttribute("erroAvaliacao", "Não foi possível identificar sua conta.");
            return null;
        }
    }

    private String primeiraMensagemDeErro(BindingResult result) {
        return result.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Preencha a nota e o comentário corretamente.");
    }

    private String redirecionarParaEvento(Long eventoId) {
        return "redirect:/eventos/" + eventoId + "#avaliacoes";
    }
}
