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
import sistema.eventos.model.Perfil;
import sistema.eventos.model.Usuario;
import sistema.eventos.service.CertificadoService;
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.UsuarioExportService;
import sistema.eventos.service.UsuarioService;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
        model.addAttribute("totalAvaliadores", usuarioService.contarPorPerfil(Perfil.ROLE_AVALIADOR));
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
        model.addAttribute("usuario", usuario);
        model.addAttribute("historico", usuarioService.listarHistoricoParticipacao(id));
        model.addAttribute("eventosOrganizados", usuarioService.listarEventosOrganizados(id));
        return "super-admin-detalhes-usuario";
    }

    @Transactional(readOnly = true)
    @GetMapping("/usuarios/{usuarioId}/inscricoes/{inscricaoId}/certificado")
    public void baixarCertificadoParticipante(
            @PathVariable Long usuarioId,
            @PathVariable Long inscricaoId,
            HttpServletResponse response) {

        Inscricao inscricao = inscricaoService.buscarPorId(inscricaoId);

        if (!inscricao.getParticipante().getId().equals(usuarioId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Este certificado não pertence ao usuário informado.");
        }

        byte[] pdfBytes = certificadoService.gerarCertificadoPdf(inscricao);

        String nomeArquivo = "certificado-evento-" + inscricaoId + ".pdf";

        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"");
        response.setHeader("Cache-Control", "private, no-store, max-age=0");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLength(pdfBytes.length);

        try (var os = response.getOutputStream()) {
            os.write(pdfBytes);
            os.flush();
        } catch (IOException e) {
            throw new NegocioException("Não foi possível escrever o certificado na resposta.");
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
}