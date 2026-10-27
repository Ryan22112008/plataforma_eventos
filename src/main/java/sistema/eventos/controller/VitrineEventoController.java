package sistema.eventos.controller;

import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import sistema.eventos.dto.AvaliacaoEventoRequestDto;
import sistema.eventos.dto.AvaliacaoEventoResponseDto;
import sistema.eventos.dto.AvaliacaoResumoDto;
import sistema.eventos.model.Evento;
import sistema.eventos.model.StatusEvento;
import sistema.eventos.model.Usuario;
import sistema.eventos.service.AvaliacaoEventoService;
import sistema.eventos.service.EventoService;
import sistema.eventos.service.UsuarioService;

@Controller
@AllArgsConstructor
public class VitrineEventoController {

    private final EventoService eventoService;
    private final AvaliacaoEventoService avaliacaoService;
    private final UsuarioService usuarioService;

     @GetMapping("/eventos/{id}")
    public String exibirDetalhesDoEvento(@PathVariable("id") Long id, Model model,
                                         Authentication authentication) {
        Evento evento = eventoService.buscarPorId(id);
        if (evento.getStatus() != StatusEvento.PUBLICADO) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("evento", evento);

        AvaliacaoResumoDto resumo = avaliacaoService.resumoDoEvento(id);
        model.addAttribute("avaliacaoResumo", resumo);
        model.addAttribute("avaliacoes", avaliacaoService.listarDoEvento(id));

        Boolean usuarioPodeAvaliar = false;
        AvaliacaoEventoResponseDto avaliacaoDoUsuario = null;
        UserDetails userDetails = usuarioLogado(authentication);
        if (userDetails != null) {
            Usuario usuario = usuarioService.buscarPorEmail(userDetails.getUsername());
            usuarioPodeAvaliar = avaliacaoService.usuarioPodeAvaliar(id, usuario.getId());
            avaliacaoDoUsuario = avaliacaoService.buscarDoUsuarioNoEvento(id, usuario.getId()).orElse(null);
            model.addAttribute("avaliacaoDoUsuario", avaliacaoDoUsuario);
        }
        model.addAttribute("usuarioPodeAvaliar", usuarioPodeAvaliar);

        AvaliacaoEventoRequestDto dto = new AvaliacaoEventoRequestDto();
        if (avaliacaoDoUsuario != null) {
            dto.setNota(avaliacaoDoUsuario.getNota());
            dto.setComentario(avaliacaoDoUsuario.getComentario());
        }
        model.addAttribute("avaliacaoDto", dto);

        return "detalhes"; // Chama o templates/detalhes.html
    }

    private UserDetails usuarioLogado(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserDetails)) {
            return null;
        }
        return (UserDetails) authentication.getPrincipal();
    }
}
