package sistema.eventos.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema.eventos.model.StatusSolicitacao;
import sistema.eventos.model.SolicitacaoOrganizador;
import sistema.eventos.model.Perfil;
import sistema.eventos.repository.SolicitacaoRepository;
import sistema.eventos.repository.UsuarioRepository;

@Controller
@RequestMapping("/admin/solicitacoes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Apenas o administrador do sistema acessa essa rota
public class AdminController {

    private final SolicitacaoRepository solicitacaoRepository;
    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public String listarSolicitacoes(Model model,
                                     @RequestParam(value = "sucesso", required = false) String sucesso) {
        model.addAttribute("solicitacoes", solicitacaoRepository.findByStatus(StatusSolicitacao.PENDENTE));
        model.addAttribute("sucesso", sucesso != null);
        return "admin-solicitacoes";
    }

    @PostMapping("/{id}/aprovar")
    @Transactional
    public String aprovarProfessor(@PathVariable Long id, RedirectAttributes attributes) {
        SolicitacaoOrganizador solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada"));

        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
            attributes.addFlashAttribute("erro", "Esta solicitação já foi processada.");
            return "redirect:/admin/solicitacoes";
        }

        // 1. Atualiza o status da solicitação
        solicitacao.setStatus(StatusSolicitacao.APROVADO);
        solicitacaoRepository.save(solicitacao);

        // 2. 🌟 PROMOVE O USUÁRIO A ORGANIZADOR
        var usuario = solicitacao.getUsuario();
        usuario.setPerfil(Perfil.ROLE_ORGANIZADOR);
        usuarioRepository.save(usuario);

        // (Opcional) Você poderia disparar um e-mail aqui pro aluno avisando: "Parabéns, agora você é um organizador!"

        attributes.addAttribute("sucesso", "true");
        return "redirect:/admin/solicitacoes";
    }
}
