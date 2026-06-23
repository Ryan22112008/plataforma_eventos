package sistema.eventos.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sistema.eventos.dto.RequestTrabalhoDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Usuario;
import sistema.eventos.service.TrabalhoService;
import sistema.eventos.service.UsuarioService;

@Controller
public class TrabalhoController {

    private final TrabalhoService trabalhoService;
    private final UsuarioService usuarioService;

    public TrabalhoController(TrabalhoService trabalhoService, UsuarioService usuarioService) {
        this.trabalhoService = trabalhoService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/participante/evento/{eventoId}/submeter")
    public String telaSubmissao(@PathVariable Long eventoId, Model model) {
        model.addAttribute("eventoId", eventoId);
        return "participante/submeter-trabalho";
    }

    @PostMapping("/participante/evento/{eventoId}/submeter")
    public String processarSubmissao(@PathVariable Long eventoId,
                                     @RequestParam("titulo") String titulo,
                                     @RequestParam("arquivo") MultipartFile arquivo,
                                     @AuthenticationPrincipal UserDetails userDetails,
                                     Model model) {
        try {
            Usuario autor = usuarioService.buscarPorEmail(userDetails.getUsername());

            // 1. Monta o DTO apenas com os campos de texto que ele possui
            RequestTrabalhoDto trabalhoDto = RequestTrabalhoDto.builder()
                    .titulo(titulo)
                    .eventoId(eventoId)
                    .build();

            // 2. CORREÇÃO: Passa o DTO, o arquivo multipart e o autor (3 argumentos)
            // Se o seu método no TrabalhoService esperar esses 3 parâmetros, o erro some na hora!
            trabalhoService.submeterTrabalho(trabalhoDto, arquivo, autor);

            return "redirect:/participante/meus-ingressos?submissaoSucesso=true";
        } catch (NegocioException e) {
            model.addAttribute("eventoId", eventoId);
            model.addAttribute("erroNegocio", e.getMessage());
            return "participante/submeter-trabalho";
        }
    }
}