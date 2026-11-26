package sistema.eventos.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.Evento;
import sistema.eventos.model.ModalidadeEvento;
import sistema.eventos.model.NichoEvento;
import sistema.eventos.model.Perfil;
import sistema.eventos.model.StatusEvento;
import sistema.eventos.model.StatusInscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.EventoRepository;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.service.CertificadoService;
import sistema.eventos.service.FileStorageService;
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.UsuarioExportService;
import sistema.eventos.service.UsuarioService;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

@Controller
@RequestMapping("/super-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private static final int TAMANHO_PAGINA = 20;

    private final UsuarioService usuarioService;
    private final UsuarioExportService usuarioExportService;
    private final InscricaoService inscricaoService;
    private final CertificadoService certificadoService;
    private final FileStorageService fileStorageService;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;

    private Long idDoUsuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String email = auth.getName();
        Usuario usuario = usuarioService.buscarPorEmail(email);
        return usuario.getId();
    }

    @GetMapping("/usuarios")
    public String listarUsuarios(
            Model model,
            @RequestParam(value = "nome", required = false) String nome,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "perfil", required = false) Perfil perfil,
            @RequestParam(value = "ativo", required = false) Boolean ativo,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 100));
        Page<Usuario> usuariosPage = usuarioService.listarUsuarios(nome, email, perfil, ativo, pageable);

        // Resumo para o topo da página (total vem da própria página paginada)
        model.addAttribute("totalUsuarios", usuariosPage.getTotalElements());
        model.addAttribute("totalParticipantes", usuarioService.contarPorPerfil(Perfil.ROLE_PARTICIPANTE));
        model.addAttribute("totalOrganizadores", usuarioService.contarPorPerfil(Perfil.ROLE_ORGANIZADOR));
        model.addAttribute("totalSuperAdmins", usuarioService.contarPorPerfil(Perfil.ROLE_SUPER_ADMIN));
        model.addAttribute("totalAtivos", usuarioService.contarPorAtivo(true));
        model.addAttribute("totalInativos", usuarioService.contarPorAtivo(false));

        // Dados da listagem
        model.addAttribute("usuarios", usuariosPage.getContent());
        model.addAttribute("currentPage", usuariosPage.getNumber());
        model.addAttribute("totalPages", usuariosPage.getTotalPages());
        model.addAttribute("totalElements", usuariosPage.getTotalElements());
        model.addAttribute("hasPrevious", usuariosPage.hasPrevious());
        model.addAttribute("hasNext", usuariosPage.hasNext());
        model.addAttribute("size", usuariosPage.getSize());

        // Filtros (para manter preenchidos no formulário)
        model.addAttribute("filtroNome", nome == null ? "" : nome.trim());
        model.addAttribute("filtroEmail", email == null ? "" : email.trim());
        model.addAttribute("filtroPerfil", perfil);
        model.addAttribute("filtroAtivo", ativo);

        // Lista de perfis para o filtro dropdown
        model.addAttribute("perfis", Perfil.values());

        return "super-admin-usuarios";
    }

    @GetMapping("/relatorios")
    public String relatorios(Model model,
            @RequestParam(value = "periodo", defaultValue = "todos") String periodo,
            @RequestParam(value = "tipo", defaultValue = "pizza") String tipo) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = switch (periodo) {
            case "30" -> hoje.minusDays(29);
            case "90" -> hoje.minusDays(89);
            case "ano" -> hoje.withDayOfYear(1);
            default -> null;
        };
        List<Usuario> usuarios = usuarioService.listarUsuarios(null, null, null, null,
                PageRequest.of(0, 100000)).getContent();
        List<Evento> todosEventos = eventoRepository.findAll();
        List<Inscricao> todasInscricoes = inscricaoRepository.findAll();
        List<Usuario> filtrados = usuarios.stream().filter(u -> inicio == null ||
                (u.getDataCriacao() != null && !u.getDataCriacao().toLocalDate().isBefore(inicio))).toList();
        List<Evento> eventos = todosEventos.stream().filter(e -> inicio == null ||
                (e.getDataCriacao() != null && !e.getDataCriacao().toLocalDate().isBefore(inicio))).toList();
        List<Inscricao> inscricoes = todasInscricoes.stream().filter(i -> inicio == null ||
                (i.getDataCriacao() != null && !i.getDataCriacao().toLocalDate().isBefore(inicio))).toList();

        model.addAttribute("periodo", periodo);
        model.addAttribute("tipo", tipo);
        model.addAttribute("inicioPeriodo", inicio);
        model.addAttribute("totalUsuarios", usuarios.size());
        model.addAttribute("totalEventos", todosEventos.size());
        model.addAttribute("totalInscricoes", todasInscricoes.size());
        model.addAttribute("totalOrganizadores", usuarioService.contarPorPerfil(Perfil.ROLE_ORGANIZADOR));
        model.addAttribute("totalParticipantes", usuarioService.contarPorPerfil(Perfil.ROLE_PARTICIPANTE));
        model.addAttribute("eventosPublicados", todosEventos.stream().filter(e -> e.getStatus() == StatusEvento.PUBLICADO).count());
        model.addAttribute("novosUsuarios", filtrados.size());
        model.addAttribute("novosEventos", eventos.size());
        model.addAttribute("novasInscricoes", inscricoes.size());
        model.addAttribute("taxaCheckin", percentual(inscricoes.stream().filter(i -> i.getStatus() == StatusInscricao.UTILIZADO).count(), inscricoes.size()));
        model.addAttribute("taxaOcupacao", percentual(eventos.stream().mapToLong(e -> Math.max(0, (e.getVagasTotais() == null ? 0 : e.getVagasTotais()) - (e.getVagasRestantes() == null ? 0 : e.getVagasRestantes()))).sum(), eventos.stream().mapToLong(e -> e.getVagasTotais() == null ? 0 : e.getVagasTotais()).sum()));
        List<Map<String, Object>> charts = new ArrayList<>();
        charts.add(chart("Perfil de usuário", usuarios, u -> rotulo(u.getPerfil().name()), Arrays.asList("Participante", "Organizador", "Avaliador", "Super admin"), u -> u.getPerfil().name()));
        charts.add(chart("Situação das contas", usuarios, u -> u.isAtivo() ? "Ativos" : "Inativos", List.of("Ativos", "Inativos"), u -> String.valueOf(u.isAtivo())));
        charts.add(chart("Status dos eventos", todosEventos, e -> rotulo(e.getStatus().name()), Arrays.stream(StatusEvento.values()).map(s -> rotulo(s.name())).toList(), e -> e.getStatus().name()));
        charts.add(chart("Modalidade dos eventos", todosEventos, e -> e.getModalidade() == null ? "Não informado" : rotulo(e.getModalidade().name()), List.of("Presencial", "Online", "Híbrido", "Não informado"), e -> String.valueOf(e.getModalidade())));
        charts.add(chart("Temas dos eventos", todosEventos, e -> e.getNicho() == null ? "Não informado" : rotulo(e.getNicho().name()), List.of("Tecnologia", "Tecnologia e inovação", "Gestão pública", "Saúde", "Outros", "Não informado"), e -> String.valueOf(e.getNicho())));
        charts.add(chart("Situação das inscrições", todasInscricoes, i -> rotulo(i.getStatus().name()), Arrays.stream(StatusInscricao.values()).map(s -> rotulo(s.name())).toList(), i -> i.getStatus().name()));
        charts.add(chart("Eventos cadastrados por trimestre", eventos, e -> e.getDataCriacao() == null ? "Sem data de cadastro" : rotuloTrimestre(e.getDataCriacao().toLocalDate()), List.of(), e -> e.getDataCriacao() == null ? "Sem data de cadastro" : rotuloTrimestre(e.getDataCriacao().toLocalDate())));
        long certificadosGerados = inscricaoRepository.countByCertificadoGeradoEmIsNotNull();
        charts.add(metricaChart("Total de participantes", usuarioService.contarPorPerfil(Perfil.ROLE_PARTICIPANTE)));
        charts.add(metricaChart("Declarações de presença geradas", certificadosGerados));
        model.addAttribute("charts", charts);
        model.addAttribute("inscricoes", todasInscricoes);
        return "super-admin-relatorios";
    }

    @GetMapping("/relatorios/excel")
    @Transactional(readOnly = true)
    public void exportarRelatoriosExcel(HttpServletResponse response) throws IOException {
        List<Usuario> usuarios = usuarioService.listarUsuarios(null, null, null, null, PageRequest.of(0, 100000)).getContent();
        List<Evento> eventos = eventoRepository.findAll();
        List<Inscricao> inscricoes = inscricaoRepository.findAll();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet resumo = workbook.createSheet("Resumo");
            String[][] linhas = {{"Indicador", "Quantidade"}, {"Usuários", String.valueOf(usuarios.size())}, {"Participantes", String.valueOf(usuarioService.contarPorPerfil(Perfil.ROLE_PARTICIPANTE))}, {"Organizadores", String.valueOf(usuarioService.contarPorPerfil(Perfil.ROLE_ORGANIZADOR))}, {"Eventos", String.valueOf(eventos.size())}, {"Inscrições", String.valueOf(inscricoes.size())}, {"Declarações de presença geradas", String.valueOf(inscricaoRepository.countByCertificadoGeradoEmIsNotNull())}};
            for (int r=0;r<linhas.length;r++){Row row=resumo.createRow(r);row.createCell(0).setCellValue(linhas[r][0]);row.createCell(1).setCellValue(linhas[r][1]);}
            Sheet userSheet=workbook.createSheet("Usuários");Row userHead=userSheet.createRow(0);String[] userCols={"Nome","E-mail","Perfil","Ativo","Data de cadastro"};for(int c=0;c<userCols.length;c++)userHead.createCell(c).setCellValue(userCols[c]);int ur=1;for(Usuario u:usuarios){Row row=userSheet.createRow(ur++);row.createCell(0).setCellValue(u.getNome());row.createCell(1).setCellValue(u.getEmail());row.createCell(2).setCellValue(rotulo(u.getPerfil().name()));row.createCell(3).setCellValue(u.isAtivo()?"Sim":"Não");row.createCell(4).setCellValue(u.getDataCriacao()==null?"":u.getDataCriacao().toString());}
<<<<<<< HEAD
            Sheet sheet=workbook.createSheet("Eventos"); Row head=sheet.createRow(0); String[] cols={"Evento","Organizador","Status","Modalidade","Tema","Inscrições","Declarações geradas"};for(int i=0;i<cols.length;i++)head.createCell(i).setCellValue(cols[i]);
            int r=1;for(Evento e:eventos){Row row=sheet.createRow(r++);row.createCell(0).setCellValue(e.getTitulo());row.createCell(1).setCellValue(e.getOrganizador().getNome());row.createCell(2).setCellValue(rotulo(e.getStatus().name()));row.createCell(3).setCellValue(e.getModalidade()==null?"Não informado":rotulo(e.getModalidade().name()));row.createCell(4).setCellValue(e.getNicho()==null?"Não informado":rotulo(e.getNicho().name()));row.createCell(5).setCellValue(inscricoes.stream().filter(i->i.getEvento().getId().equals(e.getId())).count());row.createCell(6).setCellValue(inscricaoRepository.countByEventoIdAndCertificadoGeradoEmIsNotNull(e.getId()));}
            for(Sheet sh:workbook)for(int i=0;i<sh.getRow(0).getLastCellNum();i++)sh.autoSizeColumn(i);
            Sheet insSheet=workbook.createSheet("Inscrições");Row ih=insSheet.createRow(0);String[] ic={"Participante","E-mail","Evento","Status","Inscrito em","Check-in em","Declaração gerada em"};for(int c=0;c<ic.length;c++)ih.createCell(c).setCellValue(ic[c]);int ir=1;for(Inscricao i:inscricoes){Row row=insSheet.createRow(ir++);row.createCell(0).setCellValue(i.getParticipante().getNome());row.createCell(1).setCellValue(i.getParticipante().getEmail());row.createCell(2).setCellValue(i.getEvento().getTitulo());row.createCell(3).setCellValue(rotulo(i.getStatus().name()));row.createCell(4).setCellValue(i.getDataCriacao()==null?"":i.getDataCriacao().toString());row.createCell(5).setCellValue(i.getDataCheckIn()==null?"":i.getDataCheckIn().toString());row.createCell(6).setCellValue(i.getCertificadoGeradoEm()==null?"":i.getCertificadoGeradoEm().toString());}for(int c=0;c<ih.getLastCellNum();c++)insSheet.autoSizeColumn(c);
=======
            Sheet sheet=workbook.createSheet("Eventos"); Row head=sheet.createRow(0); String[] cols={"Evento","Organizador","Status","Modalidade","Tema","Inscrições","Declarações de presença geradas"};for(int i=0;i<cols.length;i++)head.createCell(i).setCellValue(cols[i]);
            int r=1;for(Evento e:eventos){Row row=sheet.createRow(r++);row.createCell(0).setCellValue(e.getTitulo());row.createCell(1).setCellValue(e.getOrganizador().getNome());row.createCell(2).setCellValue(rotulo(e.getStatus().name()));row.createCell(3).setCellValue(e.getModalidade()==null?"Não informado":rotulo(e.getModalidade().name()));row.createCell(4).setCellValue(e.getNicho()==null?"Não informado":rotulo(e.getNicho().name()));row.createCell(5).setCellValue(inscricoes.stream().filter(i->i.getEvento().getId().equals(e.getId())).count());row.createCell(6).setCellValue(inscricaoRepository.countByEventoIdAndCertificadoGeradoEmIsNotNull(e.getId()));}
            for(Sheet sh:workbook)for(int i=0;i<sh.getRow(0).getLastCellNum();i++)sh.autoSizeColumn(i);
            Sheet insSheet=workbook.createSheet("Inscrições");Row ih=insSheet.createRow(0);String[] ic={"Participante","E-mail","Evento","Status","Inscrito em","Check-in em","Declaração de presença gerada em"};for(int c=0;c<ic.length;c++)ih.createCell(c).setCellValue(ic[c]);int ir=1;for(Inscricao i:inscricoes){Row row=insSheet.createRow(ir++);row.createCell(0).setCellValue(i.getParticipante().getNome());row.createCell(1).setCellValue(i.getParticipante().getEmail());row.createCell(2).setCellValue(i.getEvento().getTitulo());row.createCell(3).setCellValue(rotulo(i.getStatus().name()));row.createCell(4).setCellValue(i.getDataCriacao()==null?"":i.getDataCriacao().toString());row.createCell(5).setCellValue(i.getDataCheckIn()==null?"":i.getDataCheckIn().toString());row.createCell(6).setCellValue(i.getCertificadoGeradoEm()==null?"":i.getCertificadoGeradoEm().toString());}for(int c=0;c<ih.getLastCellNum();c++)insSheet.autoSizeColumn(c);
>>>>>>> 9f8190587fb70577392436388ea40a313988b188
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");response.setHeader(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=relatorios-super-admin.xlsx");workbook.write(response.getOutputStream());
        }
    }

    private long percentual(long parte, long total) { return total == 0 ? 0 : Math.round(parte * 100.0 / total); }
    private Map<String,Object> metricaChart(String titulo,long valor){Map<String,Object> m=new LinkedHashMap<>();m.put("titulo",titulo);m.put("labels",List.of(titulo));m.put("values",List.of(valor));m.put("total",valor);return m;}
    private String rotuloTrimestre(LocalDate data) { return "" + ((data.getMonthValue() - 1) / 3 + 1) + "º tri de " + data.getYear(); }
    private String rotulo(String codigo) {
        return switch (codigo) {
            case "ROLE_PARTICIPANTE" -> "Participante"; case "ROLE_ORGANIZADOR" -> "Organizador";
            case "ROLE_AVALIADOR" -> "Avaliador"; case "ROLE_SUPER_ADMIN" -> "Super admin";
            case "PUBLICADO" -> "Publicado"; case "RASCUNHO" -> "Rascunho";
            case "CANCELADO" -> "Cancelado"; case "ENCERRADO" -> "Encerrado";
            case "PRESENCIAL" -> "Presencial"; case "ONLINE" -> "Online"; case "HIBRIDO" -> "Híbrido";
            case "TECNOLOGIA_E_INOVACAO" -> "Tecnologia e inovação"; case "GESTAO_PUBLICA" -> "Gestão pública";
            case "SAUDE" -> "Saúde"; case "TECNOLOGIA" -> "Tecnologia"; case "OUTROS" -> "Outros";
<<<<<<< HEAD
            case "ATIVO" -> "Ativa"; case "PENDENTE" -> "Aguardando aprovação";
            case "RECUSADO" -> "Solicitação recusada"; case "UTILIZADO" -> "Check-in realizado";
=======
            case "ATIVO" -> "Ativa"; case "PENDENTE" -> "Pendente de aprovação";
            case "RECUSADO" -> "Recusada"; case "UTILIZADO" -> "Check-in realizado";
>>>>>>> 9f8190587fb70577392436388ea40a313988b188
            default -> codigo.substring(0, 1) + codigo.substring(1).toLowerCase(Locale.ROOT);
        };
    }
    private <T> Map<String, Object> chart(String titulo, List<T> dados, java.util.function.Function<T, String> chave,
            List<String> categorias, java.util.function.Function<T, String> categoriaRaw) {
        Map<String, Long> contagens = new java.util.LinkedHashMap<>();
        categorias.forEach(c -> contagens.put(c, 0L));
        dados.forEach(d -> { String c = chave.apply(d); if (!contagens.containsKey(c)) contagens.put(c, 0L); contagens.merge(c, 1L, Long::sum); });
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("titulo", titulo); resultado.put("labels", new ArrayList<>(contagens.keySet()));
        resultado.put("values", new ArrayList<>(contagens.values())); resultado.put("total", dados.size());
        return resultado;
    }

    @GetMapping("/organizadores")
    public String listarOrganizadores(
            Model model,
            @RequestParam(value = "nome", required = false) String nome,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "ativo", required = false) Boolean ativo,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));
        Page<Usuario> organizadoresPage = usuarioService.listarUsuarios(
                nome, email, Perfil.ROLE_ORGANIZADOR, ativo, pageable);

        Map<Long, Long> eventosPorOrganizador = usuarioService.contarEventosOrganizados(
                organizadoresPage.getContent().stream().map(Usuario::getId).collect(Collectors.toList()));

        Map<Long, String> fotosOrganizadores = new HashMap<>();
        for (Usuario organizador : organizadoresPage.getContent()) {
            fotosOrganizadores.put(organizador.getId(),
                    fileStorageService.publicImageUrl(organizador.getFotoPerfil()));
        }

        Long idLogado = idDoUsuarioLogado();
        model.addAttribute("organizadores", organizadoresPage.getContent());
        model.addAttribute("eventosPorOrganizador", eventosPorOrganizador);
        model.addAttribute("fotosOrganizadores", fotosOrganizadores);
        model.addAttribute("idUsuarioLogado", idLogado);
        model.addAttribute("totalOrganizadores", organizadoresPage.getTotalElements());
        model.addAttribute("currentPage", organizadoresPage.getNumber());
        model.addAttribute("totalPages", organizadoresPage.getTotalPages());
        model.addAttribute("totalElements", organizadoresPage.getTotalElements());
        model.addAttribute("hasPrevious", organizadoresPage.hasPrevious());
        model.addAttribute("hasNext", organizadoresPage.hasNext());
        model.addAttribute("size", organizadoresPage.getSize());
        model.addAttribute("filtroNome", nome == null ? "" : nome.trim());
        model.addAttribute("filtroEmail", email == null ? "" : email.trim());
        model.addAttribute("filtroAtivo", ativo);

        return "super-admin-organizadores";
    }

    @GetMapping("/usuarios/exportar")
    public ResponseEntity<byte[]> exportarUsuarios(
            @RequestParam(value = "nome", required = false) String nome,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "perfil", required = false) Perfil perfil,
            @RequestParam(value = "ativo", required = false) Boolean ativo) {

        byte[] excel = usuarioExportService.exportarUsuarios(nome, email, perfil, ativo);

        String data = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String nomeArquivo = "usuarios-super-admin-" + data + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/usuarios/{id}/detalhes")
    public String detalhesUsuario(@PathVariable Long id, Model model) {
        Long idLogado = idDoUsuarioLogado();
        model.addAttribute("ehPropriaConta", idLogado != null && id.equals(idLogado));

        Usuario usuario = usuarioService.buscarPorId(id);
        var historico = usuarioService.listarHistoricoParticipacao(id);
        var eventosOrganizados = usuarioService.listarEventosOrganizados(id);
        LocalDate hoje = LocalDate.now();
        long inscricoesValidasNosEventos = eventosOrganizados.stream()
                .mapToLong(evento -> evento.getTotalInscritos() == null ? 0L : evento.getTotalInscritos())
                .sum();
        long eventosAtivosOuFuturos = eventosOrganizados.stream()
                .filter(evento -> "PUBLICADO".equals(evento.getStatus()))
                .filter(evento -> {
                    LocalDate fim = evento.getDataTermino() != null ? evento.getDataTermino() : evento.getDataInicio();
                    return fim == null || !fim.isBefore(hoje);
                })
                .count();

        model.addAttribute("usuario", usuario);
        model.addAttribute("ehOrganizador", usuario.getPerfil() == Perfil.ROLE_ORGANIZADOR);
        model.addAttribute("historico", historico);
        model.addAttribute("eventosOrganizados", eventosOrganizados);
        model.addAttribute("totalEventosOrganizados", eventosOrganizados.size());
        model.addAttribute("inscricoesValidasNosEventos", inscricoesValidasNosEventos);
        model.addAttribute("eventosAtivosOuFuturos", eventosAtivosOuFuturos);
        model.addAttribute("totalParticipacoes", historico.size());
        return "super-admin-detalhes-usuario";
    }

    @Transactional
    @GetMapping({"/usuarios/{usuarioId}/inscricoes/{inscricaoId}/declaracao",
            "/usuarios/{usuarioId}/inscricoes/{inscricaoId}/certificado"})
    public void baixarCertificadoParticipante(
            @PathVariable Long usuarioId,
            @PathVariable Long inscricaoId,
            HttpServletResponse response) {

        Inscricao inscricao = inscricaoService.buscarPorId(inscricaoId);

        if (!inscricao.getParticipante().getId().equals(usuarioId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Esta declaração de presença não pertence ao usuário informado.");
        }

        byte[] pdfBytes = certificadoService.gerarCertificadoPdf(inscricao);

        String nomeArquivo = "declaracao-presenca-evento-" + inscricaoId + ".pdf";

        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"");
        response.setHeader("Cache-Control", "private, no-store, max-age=0");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLength(pdfBytes.length);

        try (var os = response.getOutputStream()) {
            os.write(pdfBytes);
            os.flush();
        } catch (IOException e) {
            throw new NegocioException("Não foi possível escrever a declaração de presença na resposta.");
        }
    }

    @PostMapping("/usuarios/{id}/desativar")
    public String desativarUsuario(@PathVariable Long id,
                                   RedirectAttributes redirectAttributes) {
        try {
            Long idLogado = idDoUsuarioLogado();
            usuarioService.desativarUsuario(id, idLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário desativado com sucesso.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/super-admin/usuarios/" + id + "/detalhes";
    }

    @PostMapping("/usuarios/{id}/reativar")
    public String reativarUsuario(@PathVariable Long id,
                                  RedirectAttributes redirectAttributes) {
        try {
            usuarioService.reativarUsuario(id);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário reativado com sucesso.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/super-admin/usuarios/" + id + "/detalhes";
    }

    @PostMapping("/usuarios/{id}/remover-organizador")
    public String removerAcessoOrganizador(@PathVariable Long id,
                                           RedirectAttributes redirectAttributes) {
        try {
            Long idLogado = idDoUsuarioLogado();
            usuarioService.removerAcessoOrganizador(id, idLogado);
            redirectAttributes.addFlashAttribute("sucesso", "Acesso de organizador removido com sucesso.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/super-admin/usuarios/" + id + "/detalhes";
    }

    @PostMapping("/usuarios/{id}/promover-super-admin")
    public String promoverSuperAdmin(@PathVariable Long id,
                                     RedirectAttributes redirectAttributes) {
        try {
            Long idLogado = idDoUsuarioLogado();
            usuarioService.promoverParaSuperAdmin(id, idLogado);
            redirectAttributes.addFlashAttribute("sucesso",
                    "Usuário promovido para SUPER_ADMIN com sucesso. O histórico e os vínculos da conta foram preservados.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/super-admin/usuarios/" + id + "/detalhes";
    }
}
