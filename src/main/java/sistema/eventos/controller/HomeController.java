package sistema.eventos.controller;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import sistema.eventos.model.Evento;
import sistema.eventos.model.ModalidadeEvento;
import sistema.eventos.model.NichoEvento;
import sistema.eventos.model.StatusEvento;
import sistema.eventos.model.StatusSolicitacao;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.SolicitacaoRepository;

import java.util.List;
import java.util.Locale;

@Controller
@AllArgsConstructor // Lombok gera o construtor automaticamente para os dois repositories/services
public class HomeController {

    private static final int EVENTOS_POR_PAGINA = 20;

    private final EventoRepository eventoRepository;
    private final SolicitacaoRepository solicitacaoRepository;

    @GetMapping({"/", "/eventos"})
    public String index(@RequestParam(value = "busca", required = false) String busca,
                        @RequestParam(value = "nicho", required = false) String nicho,
                        @RequestParam(value = "modalidade", required = false) String modalidade,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        Authentication authentication,
                        Model model) {

        boolean solicitacaoPendente = authentication != null && authentication.isAuthenticated()
                && solicitacaoRepository.existsByUsuario_EmailIgnoreCaseAndStatus(
                        authentication.getName(), StatusSolicitacao.PENDENTE);
        model.addAttribute("solicitacaoOrganizadorPendente", solicitacaoPendente);

        boolean temBusca = busca != null && !busca.trim().isEmpty();
        String nichoNormalizado = normalizarFiltro(nicho);
        String modalidadeNormalizada = normalizarFiltro(modalidade);
        NichoEvento nichoSelecionado = converterNicho(nichoNormalizado);
        ModalidadeEvento modalidadeSelecionada = converterModalidade(modalidadeNormalizada);
        boolean filtroInvalido = (!nichoNormalizado.isEmpty() && nichoSelecionado == null)
                || (!modalidadeNormalizada.isEmpty() && modalidadeSelecionada == null);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                EVENTOS_POR_PAGINA,
                Sort.by(Sort.Order.asc("dataInicio"), Sort.Order.asc("id")));
        Page<Evento> eventosPage = filtroInvalido
                ? new PageImpl<>(List.of(), pageable, 0)
                : eventoRepository.buscarEventosPublicos(
                        StatusEvento.PUBLICADO,
                        temBusca,
                        temBusca ? busca : "",
                        nichoSelecionado,
                        modalidadeSelecionada,
                        pageable);

        model.addAttribute("eventos", eventosPage.getContent());
        model.addAttribute("totalElements", eventosPage.getTotalElements());
        model.addAttribute("currentPage", eventosPage.getNumber());
        model.addAttribute("totalPages", eventosPage.getTotalPages());
        model.addAttribute("hasPrevious", eventosPage.hasPrevious());
        model.addAttribute("hasNext", eventosPage.hasNext());
        model.addAttribute("size", EVENTOS_POR_PAGINA);
        model.addAttribute("termoBusca", busca == null ? "" : busca);
        model.addAttribute("filtroNicho", nichoNormalizado);
        model.addAttribute("filtroModalidade", modalidadeNormalizada);
        return "index";
    }

    private String normalizarFiltro(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    private NichoEvento converterNicho(String valor) {
        if (valor.isEmpty()) return null;
        try {
            return NichoEvento.valueOf(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ModalidadeEvento converterModalidade(String valor) {
        if (valor.isEmpty()) return null;
        try {
            return ModalidadeEvento.valueOf(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
