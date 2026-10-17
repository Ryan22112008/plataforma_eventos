package sistema.eventos.controller;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import sistema.eventos.model.Evento;
import sistema.eventos.model.StatusEvento;
import sistema.eventos.model.StatusSolicitacao;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.SolicitacaoRepository;

import java.util.List;

@Controller
@AllArgsConstructor // Lombok gera o construtor automaticamente para os dois repositories/services
public class HomeController {

    private final EventoRepository eventoRepository;
    private final SolicitacaoRepository solicitacaoRepository;

    @GetMapping({"/", "/eventos"})
    public String index(@RequestParam(value = "busca", required = false) String busca,
                        @RequestParam(value = "nicho", required = false) String nicho,
                        @RequestParam(value = "modalidade", required = false) String modalidade,
                        Authentication authentication,
                        Model model) {

        boolean solicitacaoPendente = authentication != null && authentication.isAuthenticated()
                && solicitacaoRepository.existsByUsuario_EmailIgnoreCaseAndStatus(
                        authentication.getName(), StatusSolicitacao.PENDENTE);
        model.addAttribute("solicitacaoOrganizadorPendente", solicitacaoPendente);

        // 1. Traz a listagem padrão de ativos (Sempre começa com dados válidos)
        List<Evento> eventos = eventoRepository.buscarEventosDisponiveis(StatusEvento.PUBLICADO);

        // 2. Filtro de barra de pesquisa funcional
        if (busca != null && !busca.trim().isEmpty()) {
            eventos = eventoRepository.pesquisarEventosAtivos(busca, StatusEvento.PUBLICADO);
            model.addAttribute("termoBusca", busca); // Mantém o texto digitado na barra
        }

        // 3. Filtro dinâmico da barra lateral por Nicho (com proteção antinulo)
        if (nicho != null && !nicho.trim().isEmpty()) {
            eventos = eventos.stream()
                    .filter(e -> e.getNicho() != null && e.getNicho().name().equalsIgnoreCase(nicho))
                    .toList();
        }

        // 4. Filtro dinâmico da barra lateral por Modalidade (com proteção antinulo)
        if (modalidade != null && !modalidade.trim().isEmpty()) {
            eventos = eventos.stream()
                    .filter(e -> e.getModalidade() != null && e.getModalidade().name().equalsIgnoreCase(modalidade))
                    .toList();
        }

        // Devolve os dados filtrados para o Thymeleaf renderizar lindamente
        model.addAttribute("eventos", eventos);
        return "index";
    }

    // --- FRENTE 4: Rota para abrir a tela de detalhes estilo Even3 ---
    @GetMapping("/home/eventos/{id}")
    public String exibirDetalhesEvento(@PathVariable("id") Long id, Model model) {
        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado com o ID: " + id));
        if (evento.getStatus() != StatusEvento.PUBLICADO) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        model.addAttribute("evento", evento);
        return "detalhes";
    }
}
