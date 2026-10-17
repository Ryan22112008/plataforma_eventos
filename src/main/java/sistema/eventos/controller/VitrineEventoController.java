package sistema.eventos.controller;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import sistema.eventos.model.Evento;
import sistema.eventos.service.EventoService;
import sistema.eventos.model.StatusEvento;

@Controller
@AllArgsConstructor
public class VitrineEventoController {

    private final EventoService eventoService;

     @GetMapping("/eventos/{id}")
    public String exibirDetalhesDoEvento(@PathVariable("id") Long id, Model model) {
        Evento evento = eventoService.buscarPorId(id);
        if (evento.getStatus() != StatusEvento.PUBLICADO) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("evento", evento);
        return "detalhes"; // Chama o templates/detalhes.html
    }
}
