package sistema.eventos.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema.eventos.dto.InscritoFrequenciaDto;
import sistema.eventos.dto.RequestEventoDto;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.SorteioResultado;
import sistema.eventos.model.SorteioRodada;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.EventoService;
import sistema.eventos.service.SorteioService;
import sistema.eventos.service.UsuarioService;
import sistema.eventos.service.CertificadoService;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Slf4j
@Controller
@RequestMapping("/organizador")
@AllArgsConstructor
public class EventoController {

    private final EventoService eventoService;
    private final UsuarioService usuarioService;
    private final InscricaoService inscricaoService;
    private final SorteioService sorteioService;
    private final SessaoRepository sessaoRepository;
    private final CertificadoService certificadoService;
    private final InscricaoRepository inscricaoRepository;

    @GetMapping("/eventos/{id}/relatorios")
    public String relatoriosEvento(@PathVariable Long id, Authentication authentication, Model model) {
        Evento evento = exigirAcessoAoEvento(id, authentication);
        List<Inscricao> inscricoes = inscricaoRepository.findByEventoId(id);
        List<Map<String,Object>> charts = new java.util.ArrayList<>();
        charts.add(chartInscricoes("Situação das inscrições", inscricoes, i -> rotuloInscricao(i.getStatus())));
        charts.add(chartInscricoes("Participantes com check-in", inscricoes, i -> i.getStatus()==StatusInscricao.UTILIZADO ? "Presença confirmada" : i.getStatus()==StatusInscricao.CANCELADO ? "Inscrição cancelada" : "Aguardando presença"));
        long certificadosGerados=inscricoes.stream().filter(i->i.getCertificadoGeradoEm()!=null).count();
        long certificadosDisponiveis=inscricoes.stream().filter(i->i.getStatus()==StatusInscricao.UTILIZADO&&i.getCertificadoGeradoEm()==null).count();
        Map<String,Object> chartCertificados=new LinkedHashMap<>();chartCertificados.put("titulo","Declarações de presença geradas");chartCertificados.put("labels",List.of("Geradas","Disponíveis para geração"));chartCertificados.put("values",List.of(certificadosGerados,certificadosDisponiveis));chartCertificados.put("total",certificadosGerados+certificadosDisponiveis);charts.add(chartCertificados);
        model.addAttribute("evento",evento);model.addAttribute("inscricoes",inscricoes);model.addAttribute("charts",charts);
        model.addAttribute("totalInscricoes",inscricoes.size());
        model.addAttribute("totalCheckins",inscricoes.stream().filter(i->i.getStatus()==StatusInscricao.UTILIZADO).count());
        model.addAttribute("totalCanceladas",inscricoes.stream().filter(i->i.getStatus()==StatusInscricao.CANCELADO).count());
        model.addAttribute("totalPendentes",inscricoes.stream().filter(i->i.getStatus()==StatusInscricao.PENDENTE).count());
        model.addAttribute("totalRecusadas",inscricoes.stream().filter(i->i.getStatus()==StatusInscricao.RECUSADO).count());
        model.addAttribute("certificadosGerados",inscricaoRepository.countByEventoIdAndCertificadoGeradoEmIsNotNull(id));
        model.addAttribute("taxaOcupacao",evento.getVagasTotais()==null||evento.getVagasTotais()==0?0:Math.round((evento.getVagasTotais()-evento.getVagasRestantes())*100.0/evento.getVagasTotais()));
        model.addAttribute("paginaAtiva","relatorios");
        return "organizador-relatorios-evento";
    }

    @GetMapping("/eventos/{id}/relatorios/excel")
    public void exportarRelatorioEvento(@PathVariable Long id, Authentication authentication, HttpServletResponse response) throws java.io.IOException {
        Evento evento=exigirAcessoAoEvento(id,authentication);List<Inscricao> inscricoes=inscricaoRepository.findByEventoId(id);
        try(Workbook wb=new XSSFWorkbook()){
            Sheet resumo=wb.createSheet("Resumo");String[][] metrics={{"Evento",evento.getTitulo()},{"Inscrições",String.valueOf(inscricoes.size())},{"Check-ins",String.valueOf(inscricoes.stream().filter(i->i.getStatus()==StatusInscricao.UTILIZADO).count())},{"Canceladas",String.valueOf(inscricoes.stream().filter(i->i.getStatus()==StatusInscricao.CANCELADO).count())},{"Aguardando aprovação",String.valueOf(inscricoes.stream().filter(i->i.getStatus()==StatusInscricao.PENDENTE).count())},{"Declarações de presença geradas",String.valueOf(inscricaoRepository.countByEventoIdAndCertificadoGeradoEmIsNotNull(id))}};
            for(int r=0;r<metrics.length;r++){Row row=resumo.createRow(r);row.createCell(0).setCellValue(metrics[r][0]);row.createCell(1).setCellValue(metrics[r][1]);}
            Sheet sheet=wb.createSheet("Inscrições");Row head=sheet.createRow(0);String[] cols={"Participante","E-mail","Status","Data da inscrição","Check-in","Declaração gerada em"};for(int c=0;c<cols.length;c++)head.createCell(c).setCellValue(cols[c]);int r=1;
            for(Inscricao i:inscricoes){Row row=sheet.createRow(r++);row.createCell(0).setCellValue(i.getParticipante().getNome());row.createCell(1).setCellValue(i.getParticipante().getEmail());row.createCell(2).setCellValue(rotuloInscricao(i.getStatus()));row.createCell(3).setCellValue(i.getDataCriacao()==null?"":i.getDataCriacao().toString());row.createCell(4).setCellValue(i.getDataCheckIn()==null?"":i.getDataCheckIn().toString());row.createCell(5).setCellValue(i.getCertificadoGeradoEm()==null?"":i.getCertificadoGeradoEm().toString());}
            for(Sheet s:wb)for(int c=0;c<s.getRow(0).getLastCellNum();c++)s.autoSizeColumn(c);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");response.setHeader(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=relatorios-evento-"+id+".xlsx");wb.write(response.getOutputStream());
        }
    }

    private Map<String,Object> chartInscricoes(String titulo,List<Inscricao> inscricoes,Function<Inscricao,String> categoria){Map<String,Long> counts=new LinkedHashMap<>();inscricoes.forEach(i->counts.merge(categoria.apply(i),1L,Long::sum));Map<String,Object> m=new LinkedHashMap<>();m.put("titulo",titulo);m.put("labels",new java.util.ArrayList<>(counts.keySet()));m.put("values",new java.util.ArrayList<>(counts.values()));m.put("total",inscricoes.size());return m;}
    private String rotuloInscricao(StatusInscricao status){return switch(status){case ATIVO->"Ativa";case PENDENTE->"Aguardando aprovação";case RECUSADO->"Solicitação recusada";case UTILIZADO->"Check-in realizado";case CANCELADO->"Cancelada";};}

    @GetMapping("/painel")
    public String painelOrganizador(Model model, @AuthenticationPrincipal UserDetails userDetails,
                                    @RequestParam(value = "sucesso", required = false) String sucesso) {
        try {
            if (sucesso != null) {
                model.addAttribute("msgSucesso", "Operação realizada com sucesso!");
            }

            Usuario organizadorReal = usuarioService.buscarPorEmail(userDetails.getUsername());
            var eventosDoOrganizador = eventoService.listarPorOrganizador(organizadorReal.getId());
            model.addAttribute("eventos", eventosDoOrganizador);

            return "painel-organizador";

        } catch (Exception e) {
            log.error("Erro interno ao carregar dados do painel do organizador", e);
            model.addAttribute("erroNegocio", "Não foi possível carregar o painel neste momento.");
            return "painel-organizador";
        }
    }

    @GetMapping("/evento/novo")
    public String telaCriarEvento(Model model) {
        model.addAttribute("eventoDto", new RequestEventoDto());
        return "criar-evento";
    }

    // --- CORRIGIDO SÊNIOR: Mapeamento limpo via ModelAttribute ---
    @PostMapping("/evento/novo")
    public String processarCriacaoEvento(
            @Valid @ModelAttribute("eventoDto") RequestEventoDto dto,
            BindingResult result,
            @RequestParam("banner") MultipartFile banner, // Mantido separado pois o banner não está no DTO
            Model model,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (result.hasErrors()) {
            return "criar-evento";
        }

        try {
            Usuario organizadorReal = usuarioService.buscarPorEmail(userDetails.getUsername());

            // CORREÇÃO: O service agora recebe o pacote completo 'dto' (contendo as imagens e disciplinas) e o banner
            eventoService.criarEvento(dto, organizadorReal, banner);

            return "redirect:/organizador/painel?sucesso=true";
        } catch (NegocioException e) {
            model.addAttribute("erroNegocio", e.getMessage());
            return "criar-evento";
        }
    }

    @PostMapping("/evento/{id}/excluir")
    public String excluirEvento(@PathVariable("id") Long id,
                                java.security.Principal principal,
                                RedirectAttributes redirectAttributes) {
        try {
            eventoService.excluirEvento(id, principal.getName());
            redirectAttributes.addFlashAttribute("sucesso", "O evento foi permanentemente removido.");
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao excluir o evento {}.", id, e);
            redirectAttributes.addFlashAttribute("erroNegocio", "Não foi possível excluir o evento neste momento.");
        }
        return "redirect:/organizador/painel";
    }

    @PostMapping("/evento/{id}/certificado-template")
    public String salvarTemplateCertificado(@PathVariable("id") Long id,
                                            @RequestParam("certificadoImagem") MultipartFile certificadoImagem,
                                            @AuthenticationPrincipal UserDetails userDetails,
                                            RedirectAttributes redirectAttributes) {
        try {
            eventoService.atualizarArteCertificado(id, certificadoImagem, userDetails.getUsername());
            redirectAttributes.addFlashAttribute("sucesso", "Arte da declaração de presença atualizada!");
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao salvar imagem do certificado para o evento ID: " + id, e);
            redirectAttributes.addFlashAttribute("erroNegocio", "Não foi possível salvar a arte da declaração de presença.");
        }

        return "redirect:/organizador/painel";
    }

    @GetMapping("/eventos/{id}/inscritos")
    public String listarInscritosDoEvento(@PathVariable("id") Long id,
                                          Model model,
                                          RedirectAttributes redirectAttributes) {
        try {
            Evento evento = exigirAcessoAoEvento(id, org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
            List<InscritoFrequenciaDto> listaInscritos = inscricaoService.listarInscritosComFrequencia(id);

            model.addAttribute("evento", evento);
            model.addAttribute("inscritos", listaInscritos);
            model.addAttribute("totalPendentes", listaInscritos.stream()
                    .filter(InscritoFrequenciaDto::isSolicitacaoPendente).count());

            // 🎯 ADICIONE ESTA LINHA AQUI PARA ENVIAR O ESTADO PARA O HTML:
            model.addAttribute("paginaAtiva", "inscritos");

            return "inscritos";

        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao tentar carregar inscritos do evento ID: " + id, e);
            redirectAttributes.addFlashAttribute("erroNegocio", "Não foi possível acessar a lista de inscritos.");
            return "redirect:/organizador/painel";
        }
    }

    private static final int SORTEIOS_POR_PAGINA = 10;

    @GetMapping("/eventos/{id}/sorteios")
    public String telaSorteio(@PathVariable("id") Long id,
                              @RequestParam(value = "q", required = false) String busca,
                              @RequestParam(value = "page", required = false, defaultValue = "1") Integer pagina,
                              @RequestParam(value = "destaque", required = false) Long destaque,
                              Model model, Authentication authentication) {
        Evento evento = exigirAcessoAoEvento(id, authentication);
        int minimo = sorteioService.percentualMinimo(evento);
        List<SorteioService.ElegivelSorteio> participantesElegiveis = sorteioService.listarElegiveis(evento);
        long jaVenceram = participantesElegiveis.stream()
                .filter(SorteioService.ElegivelSorteio::vencedorAtivo)
                .count();

        List<SorteioResultado> todos = sorteioService.listarResultados(id);
        List<SorteioResultado> filtrados = sorteioService.filtrar(todos, busca);

        int totalRegistros = filtrados.size();
        int totalPaginas = Math.max(1, (totalRegistros + SORTEIOS_POR_PAGINA - 1) / SORTEIOS_POR_PAGINA);
        int paginaAtual = Math.min(Math.max(pagina == null ? 1 : pagina, 1), totalPaginas);
        int inicio = Math.min((paginaAtual - 1) * SORTEIOS_POR_PAGINA, totalRegistros);
        int fim = Math.min(inicio + SORTEIOS_POR_PAGINA, totalRegistros);

        SorteioRodada rodadaDestaque = sorteioService.buscarRodadaDoEvento(id, destaque).orElse(null);
        List<SorteioResultado> vencedoresDestaque = rodadaDestaque == null
                ? List.of()
                : sorteioService.listarVencedoresDaRodada(rodadaDestaque.getId());

        SorteioRodada rodadaApresentacao = rodadaDestaque != null
                ? rodadaDestaque
                : sorteioService.ultimaRodadaAtiva(id).orElse(null);
        List<SorteioResultado> vencedoresApresentacao = rodadaApresentacao == null
                ? List.of()
                : sorteioService.listarVencedoresDaRodada(rodadaApresentacao.getId());

        model.addAttribute("evento", evento);
        model.addAttribute("percentualMinimo", minimo);
        model.addAttribute("totalElegiveis", participantesElegiveis.size());
        model.addAttribute("totalElegiveisJaVenceram", jaVenceram);
        model.addAttribute("totalElegiveisIneditos", participantesElegiveis.size() - jaVenceram);
        model.addAttribute("participantesElegiveis", participantesElegiveis);
        model.addAttribute("resultadosSorteio", todos);
        model.addAttribute("resultadosPagina", filtrados.subList(inicio, fim));
        model.addAttribute("totalRegistros", todos.size());
        model.addAttribute("totalFiltrados", totalRegistros);
        model.addAttribute("totalPaginas", totalPaginas);
        model.addAttribute("paginaAtual", paginaAtual);
        model.addAttribute("registroInicial", totalRegistros == 0 ? 0 : inicio + 1);
        model.addAttribute("registroFinal", fim);
        model.addAttribute("filtroBusca", busca == null ? "" : busca.trim());
        model.addAttribute("totalRodadas", sorteioService.contarRodadasAtivas(id));
        model.addAttribute("ultimaRodadaAtiva", sorteioService.ultimaRodadaAtiva(id).orElse(null));
        model.addAttribute("rodadaDestaque", rodadaDestaque);
        model.addAttribute("vencedoresDestaque", vencedoresDestaque);
        model.addAttribute("rodadaApresentacao", rodadaApresentacao);
        model.addAttribute("vencedoresApresentacao", vencedoresApresentacao);
        model.addAttribute("auditoria", sorteioService.listarAuditoria(id));
        model.addAttribute("paginaAtiva", "sorteios");
        return "sorteios";
    }

    @PostMapping("/eventos/{id}/sorteios/percentual")
    public String configurarSorteio(@PathVariable("id") Long id, @RequestParam("percentualMinimo") Integer percentual,
                                    Authentication authentication, RedirectAttributes attributes) {
        exigirAcessoAoEvento(id, authentication);
        try {
            sorteioService.configurarFrequencia(id, percentual, nomeAutor(authentication));
            attributes.addFlashAttribute("toast", "Frequência mínima salva: " + percentual + "%.");
        } catch (NegocioException e) {
            attributes.addFlashAttribute("erroNegocio", e.getMessage());
        }
        return "redirect:/organizador/eventos/" + id + "/sorteios";
    }

    @PostMapping("/eventos/{id}/sorteios/realizar")
    public String realizarSorteio(@PathVariable("id") Long id,
                                  @RequestParam("quantidade") Integer quantidade,
                                  @RequestParam(value = "naoRepetirVencedores", required = false, defaultValue = "false")
                                  boolean naoRepetirVencedores,
                                  Authentication authentication,
                                  RedirectAttributes attributes) {
        exigirAcessoAoEvento(id, authentication);
        try {
            SorteioRodada rodada = sorteioService.realizarSorteio(
                    id, quantidade, naoRepetirVencedores, nomeAutor(authentication));
            return "redirect:/organizador/eventos/" + id + "/sorteios?destaque=" + rodada.getId();
        } catch (NegocioException e) {
            attributes.addFlashAttribute("erroNegocio", e.getMessage());
        } catch (Exception e) {
            log.error("Falha ao realizar sorteio do evento {} com quantidade {}.", id, quantidade, e);
            attributes.addFlashAttribute("erroNegocio", "Não foi possível realizar o sorteio agora. Tente novamente em instantes.");
        }
        return "redirect:/organizador/eventos/" + id + "/sorteios";
    }

    @PostMapping("/eventos/{id}/sorteios/{rodadaId}/desfazer")
    public String desfazerUltimaRodada(@PathVariable("id") Long id,
                                       @PathVariable("rodadaId") Long rodadaId,
                                       @RequestParam(value = "q", required = false) String busca,
                                       @RequestParam(value = "page", required = false, defaultValue = "1") Integer pagina,
                                       Authentication authentication, RedirectAttributes attributes) {
        exigirAcessoAoEvento(id, authentication);
        try {
            SorteioRodada desfeita = sorteioService.desfazerUltimaRodada(id, rodadaId, nomeAutor(authentication));
            attributes.addFlashAttribute("toast",
                    "Rodada " + desfeita.getNumeroRodada() + " desfeita. Os vencedores voltaram a ficar disponíveis.");
        } catch (NegocioException e) {
            attributes.addFlashAttribute("erroNegocio", e.getMessage());
        } catch (Exception e) {
            log.error("Falha ao desfazer a rodada {} do evento {}.", rodadaId, id, e);
            attributes.addFlashAttribute("erroNegocio", "Não foi possível desfazer a rodada agora.");
        }
        return redirectSorteios(id, busca, pagina);
    }

    @PostMapping("/eventos/{id}/sorteios/historico/limpar")
    public String limparHistoricoSorteios(@PathVariable("id") Long id,
                                          Authentication authentication,
                                          RedirectAttributes attributes) {
        exigirAcessoAoEvento(id, authentication);
        try {
            int total = sorteioService.limparHistorico(id, nomeAutor(authentication));
            attributes.addFlashAttribute("toast", total == 0
                    ? "O histórico de sorteios já estava vazio."
                    : "Histórico limpo. " + total + " resultado(s) removido(s).");
        } catch (Exception e) {
            log.error("Falha ao limpar o histórico de sorteios do evento {}.", id, e);
            attributes.addFlashAttribute("erroNegocio", "Não foi possível limpar o histórico agora.");
        }
        return "redirect:/organizador/eventos/" + id + "/sorteios";
    }

    @GetMapping("/eventos/{id}/sorteios/historico.csv")
    public ResponseEntity<byte[]> exportarHistoricoCsv(@PathVariable("id") Long id,
                                                       @RequestParam(value = "q", required = false) String busca,
                                                       Authentication authentication) {
        exigirAcessoAoEvento(id, authentication);
        List<SorteioResultado> resultados = sorteioService.filtrar(
                sorteioService.listarResultados(id), busca);
        byte[] bytes = sorteioService.gerarCsv(resultados).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"sorteios-evento-" + id + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/eventos/{id}/sorteios/historico.pdf")
    public ResponseEntity<byte[]> exportarHistoricoPdf(@PathVariable("id") Long id,
                                                       @RequestParam(value = "q", required = false) String busca,
                                                       Authentication authentication) {
        Evento evento = exigirAcessoAoEvento(id, authentication);
        List<SorteioResultado> resultados = sorteioService.filtrar(
                sorteioService.listarResultados(id), busca);
        byte[] bytes = sorteioService.gerarPdf(evento.getTitulo(), resultados);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"sorteios-evento-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    private String nomeAutor(Authentication authentication) {
        return authentication == null ? "sistema" : authentication.getName();
    }

    private String redirectSorteios(Long id, String busca, Integer pagina) {
        StringBuilder url = new StringBuilder("redirect:/organizador/eventos/")
                .append(id).append("/sorteios");
        StringBuilder query = new StringBuilder();
        adicionarParametro(query, "q", busca);
        if (pagina != null && pagina > 1) {
            adicionarParametro(query, "page", String.valueOf(pagina));
        }
        if (!query.isEmpty()) {
            url.append('?').append(query);
        }
        return url.toString();
    }

    private void adicionarParametro(StringBuilder query, String nome, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        if (!query.isEmpty()) {
            query.append('&');
        }
        query.append(nome).append('=')
                .append(java.net.URLEncoder.encode(valor, StandardCharsets.UTF_8));
    }

    /**
     * Valida a participação de um inscrito do evento. A autorização é feita
     * pelo organizador autenticado (ou SUPER_ADMIN) dono do evento e pelo
     * vínculo real entre inscrição, evento e participante no banco.
     * Nenhum percentual ou status é aceito do navegador.
     */
    @PreAuthorize("hasAnyRole('ORGANIZADOR', 'SUPER_ADMIN')")
    @PostMapping("/eventos/{eventoId}/inscricoes/{inscricaoId}/validar-participacao")
    public String validarParticipacaoDoInscrito(@PathVariable("eventoId") Long eventoId,
                                                @PathVariable("inscricaoId") Long inscricaoId,
                                                Authentication authentication,
                                                RedirectAttributes redirectAttributes) {
        try {
            exigirAcessoAoEvento(eventoId, authentication);
            inscricaoService.validarParticipacaoDoParticipante(eventoId, inscricaoId);
            redirectAttributes.addFlashAttribute("sucesso",
                    "Participação validada! A declaração de presença do participante foi liberada.");
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (NegocioException e) {
            redirectAttributes.addFlashAttribute("erroNegocio", e.getMessage());
        } catch (Exception e) {
            log.error("Erro ao validar a participação da inscrição {} no evento {}.",
                    inscricaoId, eventoId, e);
            redirectAttributes.addFlashAttribute("erroNegocio",
                    "Não foi possível validar a participação neste momento.");
        }
        return "redirect:/organizador/eventos/" + eventoId + "/inscritos";
    }

    /**
     * Valida em lote apenas as inscrições selecionadas na tela de inscritos.
     * O vínculo com o evento é conferido no service para cada ID recebido.
     */
    @PreAuthorize("hasAnyRole('ORGANIZADOR', 'SUPER_ADMIN')")
    @PostMapping("/eventos/{eventoId}/inscricoes/validar-participacao-lote")
    public String validarParticipacaoEmLote(@PathVariable("eventoId") Long eventoId,
                                            @RequestParam(value = "inscricoesSelecionadas", required = false)
                                            List<Long> inscricoesSelecionadas,
                                            Authentication authentication,
                                            RedirectAttributes redirectAttributes) {
        try {
            exigirAcessoAoEvento(eventoId, authentication);
            int validadas = inscricaoService.validarParticipacaoEmLote(eventoId, inscricoesSelecionadas);
            if (validadas > 0) {
                redirectAttributes.addFlashAttribute("sucesso",
                        "Participação validada em lote! As declarações de presença dos participantes selecionados foram liberadas.");
            } else {
                redirectAttributes.addFlashAttribute("sucesso",
                        "As participações selecionadas já estavam validadas.");
            }
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (NegocioException e) {
            redirectAttributes.addFlashAttribute("erroNegocio", e.getMessage());
        } catch (Exception e) {
            log.error("Erro ao validar em lote as inscrições do evento {}.", eventoId, e);
            redirectAttributes.addFlashAttribute("erroNegocio",
                    "Não foi possível validar as participações selecionadas neste momento.");
        }
        return "redirect:/organizador/eventos/" + eventoId + "/inscritos";
    }

    @PreAuthorize("hasAnyRole('ORGANIZADOR', 'SUPER_ADMIN')")
    @PostMapping("/eventos/{eventoId}/inscricoes/validar-participacoes")
    public String validarParticipacoesEmLote(@PathVariable Long eventoId,
                                             @RequestParam(value = "inscricaoIds", required = false) List<Long> inscricaoIds,
                                             Authentication authentication,
                                             RedirectAttributes redirectAttributes) {
        exigirAcessoAoEvento(eventoId, authentication);
        if (inscricaoIds == null || inscricaoIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("erroNegocio", "Selecione ao menos um participante para validar.");
        } else {
            int validadas = inscricaoService.validarParticipacoesEmLote(eventoId, inscricaoIds);
            redirectAttributes.addFlashAttribute("sucesso", validadas + " participante(s) validado(s). Declarações de presença liberadas.");
        }
        return "redirect:/organizador/eventos/" + eventoId + "/inscritos";
    }

    /** Aprova a solicitação de participação de um evento privado. */
    @PreAuthorize("hasAnyRole('ORGANIZADOR', 'SUPER_ADMIN')")
    @PostMapping("/eventos/{eventoId}/inscricoes/{inscricaoId}/aprovar")
    public String aprovarSolicitacao(@PathVariable("eventoId") Long eventoId,
                                     @PathVariable("inscricaoId") Long inscricaoId,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        try {
            boolean admin = ehSuperAdmin(authentication);
            inscricaoService.aprovarSolicitacao(eventoId, inscricaoId, admin ? null : authentication.getName());
            redirectAttributes.addFlashAttribute("sucesso",
                    "Solicitação aprovada! A participação foi confirmada e a vaga reservada.");
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (NegocioException e) {
            redirectAttributes.addFlashAttribute("erroNegocio", e.getMessage());
        } catch (Exception e) {
            log.error("Erro ao aprovar a solicitação {} do evento {}.", inscricaoId, eventoId, e);
            redirectAttributes.addFlashAttribute("erroNegocio", "Não foi possível aprovar a solicitação agora.");
        }
        return "redirect:/organizador/eventos/" + eventoId + "/inscritos";
    }

    /** Recusa a solicitação de participação de um evento privado. */
    @PreAuthorize("hasAnyRole('ORGANIZADOR', 'SUPER_ADMIN')")
    @PostMapping("/eventos/{eventoId}/inscricoes/{inscricaoId}/recusar")
    public String recusarSolicitacao(@PathVariable("eventoId") Long eventoId,
                                     @PathVariable("inscricaoId") Long inscricaoId,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        try {
            boolean admin = ehSuperAdmin(authentication);
            inscricaoService.recusarSolicitacao(eventoId, inscricaoId, admin ? null : authentication.getName());
            redirectAttributes.addFlashAttribute("sucesso", "Solicitação recusada. O participante não ocupará vaga.");
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (NegocioException e) {
            redirectAttributes.addFlashAttribute("erroNegocio", e.getMessage());
        } catch (Exception e) {
            log.error("Erro ao recusar a solicitação {} do evento {}.", inscricaoId, eventoId, e);
            redirectAttributes.addFlashAttribute("erroNegocio", "Não foi possível recusar a solicitação agora.");
        }
        return "redirect:/organizador/eventos/" + eventoId + "/inscritos";
    }

    private boolean ehSuperAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }

    @PostMapping("/inscricoes/{inscricaoId}/checkin")
    public String confirmarPresencaDoAluno(@PathVariable("inscricaoId") Long inscricaoId,
                                           @RequestParam("eventoId") Long eventoId,
                                           RedirectAttributes redirectAttributes,
                                           Authentication authentication) {
        try {
            exigirAcessoAoEvento(eventoId, authentication);
            Inscricao inscricao = inscricaoService.buscarPorId(inscricaoId);
            if (!inscricao.getEvento().getId().equals(eventoId)) {
                throw new org.springframework.security.access.AccessDeniedException("Inscrição não pertence a este evento.");
            }
            inscricaoService.confirmarPresenca(inscricaoId);
            redirectAttributes.addFlashAttribute("sucesso", "Presença confirmada com sucesso! Declaração de presença liberada.");
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao confirmar presença da inscrição {} no evento {}.", inscricaoId, eventoId, e);
            redirectAttributes.addFlashAttribute("erroNegocio", "Não foi possível confirmar a presença.");
        }
        return "redirect:/organizador/eventos/" + eventoId + "/inscritos";
    }
    // 🎯 ADICIONE ESTE MÉTODO DENTRO DO SEU EVENTOCONTROLLER
    @GetMapping("/evento/editar/{id}")
    public String exibirFormularioEditar(@PathVariable("id") Long id, Model model, Authentication authentication) {
        try {
            exigirAcessoAoEvento(id, authentication);
            // Busca o DTO populado com os dados e disciplinas do banco
            RequestEventoDto eventoDto = eventoService.buscarPorIdParaEdicao(id);

            // Injeta o DTO no model para o Thymeleaf preencher os inputs automaticamente
            model.addAttribute("eventoDto", eventoDto);

            // Abre o MESMO html de cadastro, que agora vai alternar para modo edição
            return "criar-evento";

        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Erro ao carregar o formulário de edição do evento {}.", id, e);
            model.addAttribute("erroNegocio", "Não foi possível carregar os dados do evento.");
            return "redirect:/organizador/painel";
        }
    }
    @PostMapping("/evento/editar/{id}")
    public String processarEdicaoEvento(
            @PathVariable("id") Long id,
            @Valid @ModelAttribute("eventoDto") RequestEventoDto dto,
            BindingResult result,
            @RequestParam(value = "banner", required = false) MultipartFile banner,
            @RequestParam(value = "imagemFrente", required = false) MultipartFile imagemFrente,
            @RequestParam(value = "imagemVerso", required = false) MultipartFile imagemVerso,
            Model model,
            Authentication authentication) {

        exigirAcessoAoEvento(id, authentication);
        // A URL identifica a edição; preserva o ID se o formulário precisar ser exibido novamente.
        dto.setId(id);

        if (result.hasErrors()) {
            preservarArtesAtuais(dto, id);
            model.addAttribute("erroNegocio", result.getFieldErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining(" ")));
            return "criar-evento";
        }

        try {
            // Chama o método sênior do service que limpa os órfãos e checa uploads vazios
            eventoService.atualizarEvento(id, dto, banner, imagemFrente, imagemVerso);
            return "redirect:/organizador/painel?sucesso=true";
        } catch (NegocioException e) {
            preservarArtesAtuais(dto, id);
            model.addAttribute("erroNegocio", e.getMessage());
            return "criar-evento";
        }
    }

    private void preservarArtesAtuais(RequestEventoDto dto, Long id) {
        RequestEventoDto atual = eventoService.buscarPorIdParaEdicao(id);
        dto.setBannerAtual(atual.getBannerAtual());
        dto.setImagemFrenteAtual(atual.getImagemFrenteAtual());
        dto.setImagemVersoAtual(atual.getImagemVersoAtual());
    }

    @PostMapping("/eventos/{id}/consolidar-presencas")
    public String consolidarDiarioPresencas(
            @PathVariable("id") Long id,
            @RequestParam(value = "inscricoesPresentes", required = false) List<Long> idsInscricoesPresentes,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {
        try {
            exigirAcessoAoEvento(id, authentication);
            // Invoca o método que criamos no InscricaoService para processar o lote
            inscricaoService.fecharDiarioClasse(id, idsInscricoesPresentes);

            redirectAttributes.addFlashAttribute("sucesso", "Diário de classe homologado! Declarações de presença liberadas para os participantes presentes.");
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao consolidar presenças do evento {}.", id, e);
            redirectAttributes.addFlashAttribute("erroNegocio", "Não foi possível consolidar as presenças.");
        }

        // Redireciona de volta para a pauta de chamada atualizada
        return "redirect:/organizador/eventos/" + id + "/inscritos";
    }
    @GetMapping("/certificado-preview/{id}")
    public String preVisualizarTemplateCertificado(@PathVariable("id") Long id, Model model, Authentication authentication) throws Exception {
        // 1. Busca os dados originais do evento
        Evento evento = exigirAcessoAoEvento(id, authentication);
        model.addAttribute("evento", evento);

        // 2. Captura os nomes dos arquivos salvos no banco
        String frenteNome = evento.getCertificadoTemplate();
        String versoNome = evento.getCertificadoTemplateVerso();

        // 3. Converte os caminhos para o formato web público (/imagens-eventos/...)
        String frenteUrl = urlPublicaImagem(frenteNome);
        String versoUrl = urlPublicaImagem(versoNome);

        // 4. 🎯 CORREÇÃO CRÍTICA: injeta as variáveis EXATAS que o seu HTML espera
        // Mesmo que o nome termine em "Base64", passar a URL pública funciona perfeitamente no navegador!
        model.addAttribute("imagemFundoBase64", frenteUrl);
        model.addAttribute("imagemVersoBase64", versoUrl);

        String codigoPreview = "EV3-PREVIEW-ONLY";
        String urlPreview = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/certificados/validar").queryParam("codigo", codigoPreview).build().encode().toUriString();
        model.addAttribute("qrCodeBase64", gerarQrCodeBase64(urlPreview));

        // 5. Mocks de segurança para o restante do documento (Evita os erros de campos nulos)
        java.util.Map<String, String> usuarioFake = new java.util.HashMap<>();
        usuarioFake.put("nome", "Nome do participante");
        model.addAttribute("usuario", usuarioFake);

        java.util.Map<String, Object> inscricaoFake = new java.util.HashMap<>();
        inscricaoFake.put("tokenIngresso", "PREVIEW-ONLY");
        inscricaoFake.put("status", "UTILIZADO");
        model.addAttribute("inscricao", inscricaoFake);

        model.addAttribute("isPreview", true);

        return "certificado";
    }

    private String urlPublicaImagem(String reference) {
        if (reference == null || reference.isBlank()) return reference;
        if (reference.startsWith("https://res.cloudinary.com/")) return reference;
        return reference.startsWith("/") ? reference : "/imagens-eventos/" + reference;
    }

    @GetMapping("/inscricoes/{inscricaoId}/certificado")
    public void baixarCertificadoComoProfessor(@PathVariable("inscricaoId") Long inscricaoId,
                                               Authentication authentication,
                                               HttpServletResponse response) {
        try {
            Inscricao inscricao = inscricaoService.buscarPorId(inscricaoId);
            exigirAcessoAoEvento(inscricao.getEvento().getId(), authentication);
            byte[] pdfBytes = certificadoService.gerarCertificadoPdf(inscricao);
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"certificado-evento-" + inscricaoId + ".pdf\"");
            response.setHeader("Cache-Control", "private, no-store, max-age=0");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setContentLength(pdfBytes.length);
            response.getOutputStream().write(pdfBytes);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Falha ao gerar certificado da inscrição {}", inscricaoId, e);
            throw new NegocioException("Não foi possível gerar a declaração de presença no momento.");
        }
    }

    /**
     * 🎯 MÉTODO AUXILIAR SÊNIOR: Gera o QR Code verdadeiro localmente usando ZXing
     */
    private String gerarQrCodeBase64(String texto) throws Exception {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        // Cria a matriz de pixels do QR Code (200x200 px)
        BitMatrix bitMatrix = qrCodeWriter.encode(texto, BarcodeFormat.QR_CODE, 200, 200);

        // Converte a matriz de pixels em uma imagem PNG em memória
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
        byte[] pngData = pngOutputStream.toByteArray();

        // Transforma os bytes do PNG em uma String Base64 compatível com a tag <img> do HTML
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
    }
    @PostMapping("/inscricoes/{id}/editar-nome")
    public String editarNomeParticipante(
            @PathVariable("id") Long inscricaoId,
            @RequestParam("novoNome") String novoNome,
            RedirectAttributes attributes,
            Authentication authentication) {
        try {
            // 1. Busca a inscrição para localizar o usuário correto
            Inscricao inscricao = inscricaoService.buscarPorId(inscricaoId);
            exigirAcessoAoEvento(inscricao.getEvento().getId(), authentication);
            if (novoNome == null || novoNome.isBlank() || novoNome.trim().length() > 100) {
                throw new NegocioException("Informe um nome válido com até 100 caracteres.");
            }
            Usuario participante = inscricao.getParticipante();

            // 2. Atualiza o nome do participante com o valor corrigido na memória
            participante.setNome(novoNome);

            // 3. Salva a alteração no banco através do método que criamos no seu Service
            usuarioService.salvar(participante);

            attributes.addFlashAttribute("mensagemSucesso", "Nome do participante corrigido com sucesso!");

            // 4. Redireciona de volta para a lista de inscritos do mesmo evento
            return "redirect:/organizador/eventos/" + inscricao.getEvento().getId() + "/inscritos";

        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Falha ao atualizar nome na inscrição {}", inscricaoId, e);
            attributes.addFlashAttribute("mensagemErro", "Não foi possível corrigir o nome do participante.");
            return "redirect:/organizador/painel";
        }
    }
    // 🎯 1. ROTA DO PROFESSOR: Exibe a tela com o QR Code gigante para projetar no telão
    @GetMapping("/eventos/{id}/projetar-presenca")
    public String projetarQrCodePresenca(@PathVariable("id") Long eventoId,
                                         Model model,
                                         @AuthenticationPrincipal UserDetails userDetails,
                                         jakarta.servlet.http.HttpServletResponse response) {
        try {
            Evento evento = eventoService.buscarPorId(eventoId);
            exigirAcessoAoEvento(eventoId, org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Referrer-Policy", "no-referrer");
            var codigosSessoes = new java.util.ArrayList<java.util.Map<String, Object>>();
            for (var sessao : sessaoRepository.findByEventoIdOrderByDataHoraInicioAsc(eventoId)) {
                String urlCheckin = ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/participante/presencas/checkin")
                        .queryParam("token", sessao.getQrCodeToken())
                        .build().toUriString();
                codigosSessoes.add(java.util.Map.of(
                        "sessao", sessao,
                        "url", urlCheckin,
                        "qrCodeBase64", gerarQrCodeBase64(urlCheckin)));
            }
            model.addAttribute("evento", evento);
            model.addAttribute("codigosSessoes", codigosSessoes);

            return "qr-presenca";
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Falha ao projetar QR codes de presença para o evento {}", eventoId, e);
            return "redirect:/organizador/painel";
        }
    }
    @PostMapping("/inscricoes/{id}/alternar")
    public String alternarPresenca(@PathVariable Long id, @RequestParam Long eventoId, Authentication authentication) {
        exigirAcessoAoEvento(eventoId, authentication);
        Inscricao inscricao = inscricaoService.buscarPorId(id);
        if (!inscricao.getEvento().getId().equals(eventoId)) {
            throw new org.springframework.security.access.AccessDeniedException("Inscrição não pertence a este evento.");
        }
        inscricaoService.alternarPresenca(id);
        return "redirect:/organizador/eventos/" + eventoId + "/inscritos";
    }

    /**
     * Relatório consolidado de todos os eventos do organizador autenticado.
     * Diferente do relatório por evento, reúne inscrições, presenças,
     * solicitações pendentes e declarações de presença de toda a carteira.
     */
    @GetMapping("/relatorios")
    public String relatoriosConsolidados(Authentication authentication, Model model) {
        Usuario organizador = usuarioService.buscarPorEmail(authentication.getName());
        List<Evento> eventos = eventoService.listarPorOrganizador(organizador.getId());
        List<Inscricao> inscricoes = inscricaoRepository.findByOrganizadorIdComRelacoes(organizador.getId());

        long totalInscricoes = inscricoes.size();
        long totalCheckins = contarStatus(inscricoes, StatusInscricao.UTILIZADO);
        long totalAtivos = contarStatus(inscricoes, StatusInscricao.ATIVO);
        long totalPendentes = contarStatus(inscricoes, StatusInscricao.PENDENTE);
        long totalRecusadas = contarStatus(inscricoes, StatusInscricao.RECUSADO);
        long totalCanceladas = contarStatus(inscricoes, StatusInscricao.CANCELADO);
        long totalDeclaracoes = inscricoes.stream().filter(i -> i.getCertificadoGeradoEm() != null).count();
        long eventosPublicados = eventos.stream()
                .filter(e -> e.getStatus() != null && "PUBLICADO".equals(e.getStatus().name())).count();
        long eventosPrivados = eventos.stream().filter(Evento::isEventoPrivado).count();

        int vagasTotais = eventos.stream().mapToInt(e -> e.getVagasTotais() == null ? 0 : e.getVagasTotais()).sum();
        int vagasRestantes = eventos.stream().mapToInt(e -> e.getVagasRestantes() == null ? 0 : e.getVagasRestantes()).sum();
        int taxaOcupacao = vagasTotais <= 0 ? 0
                : (int) Math.max(0, Math.min(100, Math.round((vagasTotais - vagasRestantes) * 100.0 / vagasTotais)));
        long confirmadas = totalAtivos + totalCheckins;
        int taxaPresenca = confirmadas == 0 ? 0
                : (int) Math.round(totalCheckins * 100.0 / confirmadas);

        List<Map<String, Object>> charts = new java.util.ArrayList<>();
        charts.add(serieChart("Situação geral das inscrições",
                List.of("Presença confirmada", "Inscrições ativas", "Aguardando aprovação", "Recusadas", "Canceladas"),
                List.of(totalCheckins, totalAtivos, totalPendentes, totalRecusadas, totalCanceladas)));

        Map<Long, Long> inscricoesPorEvento = new LinkedHashMap<>();
        Map<Long, Long> checkinsPorEvento = new LinkedHashMap<>();
        for (Evento evento : eventos) {
            inscricoesPorEvento.put(evento.getId(), 0L);
            checkinsPorEvento.put(evento.getId(), 0L);
        }
        for (Inscricao inscricao : inscricoes) {
            if (inscricao.getEvento() == null || inscricao.getEvento().getId() == null) continue;
            Long id = inscricao.getEvento().getId();
            inscricoesPorEvento.merge(id, 1L, Long::sum);
            if (inscricao.getStatus() == StatusInscricao.UTILIZADO) {
                checkinsPorEvento.merge(id, 1L, Long::sum);
            }
        }
        charts.add(serieChart("Inscrições por evento",
                eventos.stream().map(e -> abreviar(e.getTitulo(), 24)).toList(),
                eventos.stream().map(e -> inscricoesPorEvento.getOrDefault(e.getId(), 0L)).toList()));
        charts.add(serieChart("Check-ins por evento",
                eventos.stream().map(e -> abreviar(e.getTitulo(), 24)).toList(),
                eventos.stream().map(e -> checkinsPorEvento.getOrDefault(e.getId(), 0L)).toList()));
        charts.add(serieChart("Eventos por situação",
                List.of("Publicados", "Rascunhos", "Encerrados", "Cancelados"),
                List.of(eventosPublicados,
                        eventos.stream().filter(e -> e.getStatus() != null && "RASCUNHO".equals(e.getStatus().name())).count(),
                        eventos.stream().filter(e -> e.getStatus() != null && "ENCERRADO".equals(e.getStatus().name())).count(),
                        eventos.stream().filter(e -> e.getStatus() != null && "CANCELADO".equals(e.getStatus().name())).count())));

        List<Map<String, Object>> resumoEventos = new java.util.ArrayList<>();
        for (Evento evento : eventos) {
            long doEvento = inscricoesPorEvento.getOrDefault(evento.getId(), 0L);
            long checkins = checkinsPorEvento.getOrDefault(evento.getId(), 0L);
            long pendentes = inscricoes.stream()
                    .filter(i -> i.getEvento() != null && evento.getId().equals(i.getEvento().getId()))
                    .filter(i -> i.getStatus() == StatusInscricao.PENDENTE).count();
            long canceladas = inscricoes.stream()
                    .filter(i -> i.getEvento() != null && evento.getId().equals(i.getEvento().getId()))
                    .filter(i -> i.getStatus() == StatusInscricao.CANCELADO).count();
            long declaracoes = inscricoes.stream()
                    .filter(i -> i.getEvento() != null && evento.getId().equals(i.getEvento().getId()))
                    .filter(i -> i.getCertificadoGeradoEm() != null).count();
            int ocupacao = evento.getVagasTotais() == null || evento.getVagasTotais() <= 0 ? 0
                    : (int) Math.max(0, Math.min(100, Math.round(
                    (evento.getVagasTotais() - (evento.getVagasRestantes() == null ? 0 : evento.getVagasRestantes()))
                            * 100.0 / evento.getVagasTotais())));
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("id", evento.getId());
            linha.put("titulo", evento.getTitulo());
            linha.put("status", evento.getStatus() == null ? "Sem status" : rotuloStatusEvento(evento.getStatus().name()));
            linha.put("privado", evento.isEventoPrivado());
            linha.put("inscricoes", doEvento);
            linha.put("checkins", checkins);
            linha.put("pendentes", pendentes);
            linha.put("canceladas", canceladas);
            linha.put("declaracoes", declaracoes);
            linha.put("ocupacao", ocupacao);
            resumoEventos.add(linha);
        }

        model.addAttribute("organizador", organizador);
        model.addAttribute("totalEventos", eventos.size());
        model.addAttribute("eventosPublicados", eventosPublicados);
        model.addAttribute("eventosPrivados", eventosPrivados);
        model.addAttribute("totalInscricoes", totalInscricoes);
        model.addAttribute("totalCheckins", totalCheckins);
        model.addAttribute("totalPendentes", totalPendentes);
        model.addAttribute("totalRecusadas", totalRecusadas);
        model.addAttribute("totalCanceladas", totalCanceladas);
        model.addAttribute("totalDeclaracoes", totalDeclaracoes);
        model.addAttribute("taxaOcupacao", taxaOcupacao);
        model.addAttribute("taxaPresenca", taxaPresenca);
        model.addAttribute("charts", charts);
        model.addAttribute("resumoEventos", resumoEventos);
        model.addAttribute("paginaAtiva", "relatorios");
        return "organizador-relatorios";
    }

    @GetMapping("/relatorios/excel")
    public void exportarRelatoriosConsolidados(Authentication authentication, HttpServletResponse response)
            throws java.io.IOException {
        Usuario organizador = usuarioService.buscarPorEmail(authentication.getName());
        List<Evento> eventos = eventoService.listarPorOrganizador(organizador.getId());
        List<Inscricao> inscricoes = inscricaoRepository.findByOrganizadorIdComRelacoes(organizador.getId());

        long totalCheckins = contarStatus(inscricoes, StatusInscricao.UTILIZADO);
        long totalPendentes = contarStatus(inscricoes, StatusInscricao.PENDENTE);
        long totalDeclaracoes = inscricoes.stream().filter(i -> i.getCertificadoGeradoEm() != null).count();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet resumo = wb.createSheet("Resumo");
            String[][] metricas = {
                    {"Organizador", organizador.getNome()},
                    {"Eventos", String.valueOf(eventos.size())},
                    {"Inscrições", String.valueOf(inscricoes.size())},
                    {"Check-ins", String.valueOf(totalCheckins)},
                    {"Solicitações pendentes", String.valueOf(totalPendentes)},
                    {"Declarações de presença geradas", String.valueOf(totalDeclaracoes)}
            };
            for (int r = 0; r < metricas.length; r++) {
                Row row = resumo.createRow(r);
                row.createCell(0).setCellValue(metricas[r][0]);
                row.createCell(1).setCellValue(metricas[r][1]);
            }

            Sheet sheetEventos = wb.createSheet("Eventos");
            String[] colunasEventos = {"Evento", "Situação", "Privado", "Inscrições", "Check-ins",
                    "Aguardando aprovação", "Canceladas", "Declarações geradas", "Ocupação (%)"};
            Row cabecalhoEventos = sheetEventos.createRow(0);
            for (int c = 0; c < colunasEventos.length; c++) {
                cabecalhoEventos.createCell(c).setCellValue(colunasEventos[c]);
            }
            int linha = 1;
            for (Evento evento : eventos) {
                long doEvento = inscricoes.stream()
                        .filter(i -> i.getEvento() != null && evento.getId().equals(i.getEvento().getId())).count();
                long checkins = inscricoes.stream()
                        .filter(i -> i.getEvento() != null && evento.getId().equals(i.getEvento().getId()))
                        .filter(i -> i.getStatus() == StatusInscricao.UTILIZADO).count();
                long pendentes = inscricoes.stream()
                        .filter(i -> i.getEvento() != null && evento.getId().equals(i.getEvento().getId()))
                        .filter(i -> i.getStatus() == StatusInscricao.PENDENTE).count();
                long canceladas = inscricoes.stream()
                        .filter(i -> i.getEvento() != null && evento.getId().equals(i.getEvento().getId()))
                        .filter(i -> i.getStatus() == StatusInscricao.CANCELADO).count();
                long declaracoes = inscricoes.stream()
                        .filter(i -> i.getEvento() != null && evento.getId().equals(i.getEvento().getId()))
                        .filter(i -> i.getCertificadoGeradoEm() != null).count();
                int ocupacao = evento.getVagasTotais() == null || evento.getVagasTotais() <= 0 ? 0
                        : (int) Math.max(0, Math.min(100, Math.round(
                        (evento.getVagasTotais() - (evento.getVagasRestantes() == null ? 0 : evento.getVagasRestantes()))
                                * 100.0 / evento.getVagasTotais())));
                Row row = sheetEventos.createRow(linha++);
                row.createCell(0).setCellValue(evento.getTitulo());
                row.createCell(1).setCellValue(evento.getStatus() == null ? "Sem status" : rotuloStatusEvento(evento.getStatus().name()));
                row.createCell(2).setCellValue(evento.isEventoPrivado() ? "Sim" : "Não");
                row.createCell(3).setCellValue(doEvento);
                row.createCell(4).setCellValue(checkins);
                row.createCell(5).setCellValue(pendentes);
                row.createCell(6).setCellValue(canceladas);
                row.createCell(7).setCellValue(declaracoes);
                row.createCell(8).setCellValue(ocupacao);
            }
            for (Sheet sheet : wb) {
                if (sheet.getRow(0) != null) {
                    for (int c = 0; c < sheet.getRow(0).getLastCellNum(); c++) sheet.autoSizeColumn(c);
                }
            }
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=relatorios-organizador.xlsx");
            wb.write(response.getOutputStream());
        }
    }

    private long contarStatus(List<Inscricao> inscricoes, StatusInscricao status) {
        return inscricoes.stream().filter(i -> i.getStatus() == status).count();
    }

    private Map<String, Object> serieChart(String titulo, List<String> labels, List<Long> values) {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("titulo", titulo);
        chart.put("labels", new java.util.ArrayList<>(labels));
        chart.put("values", new java.util.ArrayList<>(values));
        chart.put("total", values.stream().mapToLong(Long::longValue).sum());
        return chart;
    }

    private String abreviar(String texto, int limite) {
        if (texto == null) return "";
        return texto.length() <= limite ? texto : texto.substring(0, limite - 1) + "…";
    }

    private String rotuloStatusEvento(String status) {
        return switch (status) {
            case "PUBLICADO" -> "Publicado";
            case "RASCUNHO" -> "Rascunho";
            case "CANCELADO" -> "Cancelado";
            case "ENCERRADO" -> "Encerrado";
            default -> status;
        };
    }

    private Evento exigirAcessoAoEvento(Long eventoId, Authentication authentication) {
        Evento evento = eventoService.buscarPorId(eventoId);
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
        if (admin) return evento;
        if (authentication == null || evento.getOrganizador() == null
                || !evento.getOrganizador().getEmail().equalsIgnoreCase(authentication.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Você não tem acesso a este evento.");
        }
        return evento;
    }
}
