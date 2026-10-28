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

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Controller
@AllArgsConstructor // Lombok gera o construtor automaticamente para os dois repositories/services
public class HomeController {

    private static final int EVENTOS_POR_PAGINA = 20;

    /** Estados de vigência aceitos pelo filtro público. */
    private static final Set<String> VIGENCIAS_VALIDAS = Set.of("FUTUROS", "EM_ANDAMENTO", "ENCERRADOS");

    private final EventoRepository eventoRepository;
    private final SolicitacaoRepository solicitacaoRepository;
    private final Clock relogio;

    @GetMapping({"/", "/eventos"})
    public String index(@RequestParam(value = "busca", required = false) String busca,
                        @RequestParam(value = "nicho", required = false) String nicho,
                        @RequestParam(value = "modalidade", required = false) String modalidade,
                        @RequestParam(value = "data", required = false) String data,
                        @RequestParam(value = "vigencia", required = false) String vigencia,
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
        String dataNormalizada = normalizarFiltro(data);
        String vigenciaNormalizada = normalizarFiltro(vigencia);
        NichoEvento nichoSelecionado = converterNicho(nichoNormalizado);
        ModalidadeEvento modalidadeSelecionada = converterModalidade(modalidadeNormalizada);
        LocalDate dataSelecionada = converterData(dataNormalizada);
        String vigenciaSelecionada = converterVigencia(vigenciaNormalizada);
        boolean filtroInvalido = (!nichoNormalizado.isEmpty() && nichoSelecionado == null)
                || (!modalidadeNormalizada.isEmpty() && modalidadeSelecionada == null)
                || (!dataNormalizada.isEmpty() && dataSelecionada == null)
                || (!vigenciaNormalizada.isEmpty() && vigenciaSelecionada == null);

        // Data de referência da vigência: dia do calendário no fuso da aplicação.
        LocalDate hoje = LocalDate.now(relogio);

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
                        dataSelecionada,
                        vigenciaSelecionada,
                        hoje,
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
        model.addAttribute("filtroData", dataNormalizada);
        model.addAttribute("filtroVigencia", vigenciaNormalizada);
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

    private LocalDate converterData(String valor) {
        if (valor.isEmpty()) return null;
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String converterVigencia(String valor) {
        return VIGENCIAS_VALIDAS.contains(valor) ? valor : null;
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
