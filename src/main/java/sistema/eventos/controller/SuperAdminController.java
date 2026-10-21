package sistema.eventos.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import sistema.eventos.model.Perfil;
import sistema.eventos.model.Usuario;
import sistema.eventos.service.UsuarioService;

import java.util.Locale;

@Controller
@RequestMapping("/super-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private static final int TAMANHO_PAGINA = 20;

    private final UsuarioService usuarioService;

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

    @GetMapping("/usuarios/{id}/detalhes")
    public String detalhesUsuario(@PathVariable Long id, Model model) {
        Long idLogado = idDoUsuarioLogado();
        model.addAttribute("ehPropriaConta", idLogado != null && id.equals(idLogado));

        Usuario usuario = usuarioService.buscarPorId(id);
        model.addAttribute("usuario", usuario);
        return "super-admin-detalhes-usuario";
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