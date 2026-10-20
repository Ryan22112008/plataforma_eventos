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
import sistema.eventos.service.EmailService;
import java.util.Locale;

@Controller
@RequestMapping("/admin/solicitacoes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Apenas o administrador do sistema acessa essa rota
public class AdminController {

    private final SolicitacaoRepository solicitacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;

    @GetMapping
    public String listarSolicitacoes(Model model,
                                     @RequestParam(value = "sucesso", required = false) String sucesso,
                                     @RequestParam(value = "erro", required = false) String erro) {
        model.addAttribute("solicitacoes", solicitacaoRepository.findByStatus(StatusSolicitacao.PENDENTE));
        model.addAttribute("sucesso", sucesso != null);
        model.addAttribute("erro", erro);
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

        // Bloqueia aprovação de registros legados com dados obrigatórios incompletos.
        // Evita ConstraintViolationException (@NotBlank) no commit do Hibernate.
        if (solicitacao.isDadosIncompletos()) {
            attributes.addFlashAttribute("erro",
                    "Não é possível aprovar: esta solicitação possui dados obrigatórios incompletos "
                            + "(nome completo, matrícula, e-mail institucional e justificativa). "
                            + "Edite a solicitação antes de aprovar.");
            return "redirect:/admin/solicitacoes";
        }

        // 1. Atualiza o status da solicitação
        solicitacao.setStatus(StatusSolicitacao.APROVADO);
        solicitacaoRepository.save(solicitacao);

        // 2. PROMOVE O USUÁRIO A ORGANIZADOR
        var usuario = solicitacao.getUsuario();
        usuario.setPerfil(Perfil.ROLE_ORGANIZADOR);
        usuarioRepository.save(usuario);

        // 3. Notifica o usuário por e-mail
        emailService.enviarSolicitacaoAprovada(usuario);

        attributes.addAttribute("sucesso", "true");
        return "redirect:/admin/solicitacoes";
    }

    @PostMapping("/{id}/rejeitar")
    @Transactional
    public String rejeitarSolicitacao(@PathVariable Long id, RedirectAttributes attributes) {
        SolicitacaoOrganizador solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada"));

        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
            attributes.addFlashAttribute("erro", "Esta solicitação já foi processada.");
            return "redirect:/admin/solicitacoes";
        }

        // 1. Atualiza o status para REJEITADO (NÃO altera o perfil do usuário)
        solicitacao.setStatus(StatusSolicitacao.REJEITADO);
        solicitacaoRepository.save(solicitacao);

        // 2. Notifica o usuário por e-mail
        emailService.enviarSolicitacaoRejeitada(solicitacao.getUsuario());

        attributes.addAttribute("sucesso", "true");
        return "redirect:/admin/solicitacoes";
    }

    @GetMapping("/{id}/editar")
    public String editarSolicitacao(@PathVariable Long id, Model model) {
        SolicitacaoOrganizador solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada"));
        model.addAttribute("solicitacao", solicitacao);
        return "editar-solicitacao";
    }

    @PostMapping("/{id}/editar")
    @Transactional
    public String salvarEdicaoSolicitacao(@PathVariable Long id,
                                          @RequestParam String nomeCompleto,
                                          @RequestParam String matricula,
                                          @RequestParam String emailInstitucional,
                                          @RequestParam String justificativa,
                                          RedirectAttributes attributes) {
        SolicitacaoOrganizador solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada"));

        solicitacao.setNomeCompleto(nomeCompleto.trim());
        solicitacao.setMatricula(matricula.trim());
        solicitacao.setEmailInstitucional(emailInstitucional.trim().toLowerCase(Locale.ROOT));
        solicitacao.setJustificativa(justificativa.trim());
        solicitacaoRepository.save(solicitacao);

        attributes.addFlashAttribute("sucesso", "Dados da solicitação atualizados com sucesso.");
        return "redirect:/admin/solicitacoes";
    }
}
