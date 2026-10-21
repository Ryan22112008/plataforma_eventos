package sistema.eventos.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model; // ADICIONADO: Necessário para passar dados ao HTML
import org.springframework.web.bind.annotation.GetMapping;
import sistema.eventos.model.Evento;
import sistema.eventos.model.Usuario;
import sistema.eventos.model.StatusSolicitacao;
import sistema.eventos.repository.SolicitacaoRepository;
import sistema.eventos.service.EventoService; // ADICIONADO: Injeção do serviço de eventos
import sistema.eventos.service.UsuarioService; // ADICIONADO: Injeção do serviço de usuários

import java.util.List;
import java.util.Set;

@Controller
public class DashboardController {

    private final EventoService eventoService;
    private final UsuarioService usuarioService;
    private final SolicitacaoRepository solicitacaoRepository;

    // Injeção de dependências via construtor (Melhor prática de arquitetura)
    public DashboardController(EventoService eventoService, UsuarioService usuarioService,
                               SolicitacaoRepository solicitacaoRepository) {
        this.eventoService = eventoService;
        this.usuarioService = usuarioService;
        this.solicitacaoRepository = solicitacaoRepository;
    }

    @GetMapping("/dashboard")
    public String abrirDashboard(Authentication authentication, Model model) { // ADICIONADO: parâmetro Model
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());

        // Roteamento inteligente baseado no perfil de acesso (Roles)
        if (roles.contains("ROLE_ADMIN")) {
            return "redirect:/admin/solicitacoes";
        } else if (roles.contains("ROLE_ORGANIZADOR")) {

            // --- RECHEIO DO MODEL (O QUE ESTAVA FALTANDO) ---
            // 1. Recupera o e-mail de quem acabou de logar
            String emailLogado = authentication.getName();

            // 2. Busca o objeto do usuário para descobrir o ID real dele
            Usuario organizador = usuarioService.buscarPorEmail(emailLogado);

            // 3. Consulta os eventos daquele ID e joga no Model para o Thymeleaf ler
            List<Evento> eventos = eventoService.listarPorOrganizador(organizador.getId());
            model.addAttribute("eventos", eventos);
            // ------------------------------------------------

            // Aponta para templates/painel-organizador.html com os dados preenchidos
            return "painel-organizador";

        } else if (roles.contains("ROLE_PARTICIPANTE")) {
            if (solicitacaoRepository.existsByUsuario_EmailIgnoreCaseAndStatus(
                    authentication.getName(), StatusSolicitacao.PENDENTE)) {
                return "redirect:/eventos";
            }
            return "redirect:/eventos";
        } else if (roles.contains("ROLE_SUPER_ADMIN")) {
            return "redirect:/super-admin/usuarios";
        }

        // Caso caia em um limbo sem role definida, força o retorno para a home de segurança
        return "redirect:/login";
    }
}
