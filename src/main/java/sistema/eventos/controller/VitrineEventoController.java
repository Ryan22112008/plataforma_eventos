package sistema.eventos.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import sistema.eventos.dto.AvaliacaoEventoRequestDto;
import sistema.eventos.dto.AvaliacaoEventoResponseDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.StatusEvento;
import sistema.eventos.model.Usuario;
import sistema.eventos.service.AvaliacaoEventoService;
import sistema.eventos.service.EventoService;
import sistema.eventos.service.UsuarioService;

@Controller
@RequiredArgsConstructor
public class VitrineEventoController {

    private static final int TAMANHO_PAGINA_AVALIACOES = 10;

    private final EventoService eventoService;
    private final AvaliacaoEventoService avaliacaoEventoService;
    private final UsuarioService usuarioService;

    @GetMapping("/eventos/{id}")
    public String exibirDetalhesDoEvento(@PathVariable("id") Long id,
                                         @RequestParam(name = "paginaAvaliacoes", defaultValue = "0") int paginaAvaliacoes,
                                         @AuthenticationPrincipal UserDetails userDetails,
                                         Model model) {
        Evento evento = eventoService.buscarPorId(id);
        if (evento.getStatus() != StatusEvento.PUBLICADO) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("evento", evento);

        Page<AvaliacaoEventoResponseDto> pagina = carregarAvaliacoes(id, paginaAvaliacoes);
        model.addAttribute("avaliacoes", pagina.getContent());
        model.addAttribute("paginaAtualAvaliacoes", pagina.getNumber());
        model.addAttribute("totalPaginasAvaliacoes", pagina.getTotalPages());
        model.addAttribute("temPaginaAnteriorAvaliacoes", pagina.hasPrevious());
        model.addAttribute("temProximaPaginaAvaliacoes", pagina.hasNext());
        model.addAttribute("avaliacaoResumo", avaliacaoEventoService.obterResumo(id));

        Usuario usuario = usuarioAutenticado(userDetails);
        AvaliacaoEventoResponseDto avaliacaoDoUsuario = usuario == null
                ? null
                : avaliacaoEventoService.buscarDoUsuarioNoEvento(id, usuario.getId()).orElse(null);
        boolean ehOrganizador = usuario != null && evento.getOrganizador() != null
                && usuario.getId().equals(evento.getOrganizador().getId());

        model.addAttribute("usuarioAutenticado", usuario != null);
        model.addAttribute("ehOrganizador", ehOrganizador);
        model.addAttribute("avaliacaoDoUsuario", avaliacaoDoUsuario);
        model.addAttribute("usuarioPodeAvaliar", usuario != null && avaliacaoDoUsuario == null
                && avaliacaoEventoService.podeAvaliar(evento, usuario.getId()));

        AvaliacaoEventoRequestDto avaliacaoDto = new AvaliacaoEventoRequestDto();
        if (avaliacaoDoUsuario != null) {
            avaliacaoDto.setNota(avaliacaoDoUsuario.getNota());
            avaliacaoDto.setComentario(avaliacaoDoUsuario.getComentario());
        }
        model.addAttribute("avaliacaoDto", avaliacaoDto);

        return "detalhes"; // Chama o templates/detalhes.html
    }

    private Page<AvaliacaoEventoResponseDto> carregarAvaliacoes(Long eventoId, int paginaSolicitada) {
        PageRequest pageable = PageRequest.of(
                Math.max(paginaSolicitada, 0),
                TAMANHO_PAGINA_AVALIACOES,
                Sort.by(Sort.Direction.DESC, "dataCriacao"));
        Page<AvaliacaoEventoResponseDto> pagina = avaliacaoEventoService.listarDoEvento(eventoId, pageable);

        if (pagina.getTotalPages() > 0 && pagina.getNumber() >= pagina.getTotalPages()) {
            pageable = PageRequest.of(
                    pagina.getTotalPages() - 1,
                    TAMANHO_PAGINA_AVALIACOES,
                    Sort.by(Sort.Direction.DESC, "dataCriacao"));
            pagina = avaliacaoEventoService.listarDoEvento(eventoId, pageable);
        }
        return pagina;
    }

    private Usuario usuarioAutenticado(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        try {
            return usuarioService.buscarPorEmail(userDetails.getUsername());
        } catch (NegocioException ex) {
            return null;
        }
    }
}
