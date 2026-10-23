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
@RequestMapping("/super-admin/solicitacoes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminSolicitacaoController {

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
        return "super-admin-solicitacoes";
    }

    @PostMapping("/{id}/aprovar")
    @Transactional
    public String aprovarProfessor(@PathVariable Long id, RedirectAttributes attributes) {
        SolicitacaoOrganizador solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada"));

        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
            attributes.addFlashAttribute("erro", "Esta solicitação já foi processada.");
            return "redirect:/super-admin/solicitacoes";
        }

        if (solicitacao.isDadosIncompletos()) {
            attributes.addFlashAttribute("erro",
                    "Não é possível aprovar: esta solicitação possui dados obrigatórios incompletos "
                            + "(nome completo, matrícula, e-mail institucional e justificativa). "
                            + "Edite a solicitação antes de aprovar.");
            return "redirect:/super-admin/solicitacoes";
        }

        solicitacao.setStatus(StatusSolicitacao.APROVADO);
        solicitacaoRepository.save(solicitacao);

        var usuario = solicitacao.getUsuario();
        usuario.setPerfil(Perfil.ROLE_ORGANIZADOR);
        usuarioRepository.save(usuario);

        emailService.enviarSolicitacaoAprovada(usuario);

        attributes.addAttribute("sucesso", "true");
        return "redirect:/super-admin/solicitacoes";
    }

    @PostMapping("/{id}/rejeitar")
    @Transactional
    public String rejeitarSolicitacao(@PathVariable Long id, RedirectAttributes attributes) {
        SolicitacaoOrganizador solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada"));

        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
            attributes.addFlashAttribute("erro", "Esta solicitação já foi processada.");
            return "redirect:/super-admin/solicitacoes";
        }

        solicitacao.setStatus(StatusSolicitacao.REJEITADO);
        solicitacaoRepository.save(solicitacao);

        emailService.enviarSolicitacaoRejeitada(solicitacao.getUsuario());

        attributes.addAttribute("sucesso", "true");
        return "redirect:/super-admin/solicitacoes";
    }

    @GetMapping("/{id}/editar")
    public String editarSolicitacao(@PathVariable Long id, Model model) {
        SolicitacaoOrganizador solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada"));
        model.addAttribute("solicitacao", solicitacao);
        return "super-admin-editar-solicitacao";
    }

    @PostMapping("/{id}/editar")
    @Transactional
    public String salvarEdicaoSolicitacao(@PathVariable Long id,
                                          @RequestParam String nomeCompleto,
                                          @RequestParam String matricula,
                                          @RequestParam String emailInstitucional,
                                          @RequestParam String justificativa,
                                          @RequestParam String orgao,
                                          RedirectAttributes attributes) {
        SolicitacaoOrganizador solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada"));

        solicitacao.setNomeCompleto(nomeCompleto.trim());
        solicitacao.setMatricula(matricula.trim());
        solicitacao.setEmailInstitucional(emailInstitucional.trim().toLowerCase(Locale.ROOT));
        solicitacao.setJustificativa(justificativa.trim());
        solicitacao.setOrgao(orgao == null ? null : orgao.trim());
        solicitacaoRepository.save(solicitacao);

        attributes.addFlashAttribute("sucesso", "Dados da solicitação atualizados com sucesso.");
        return "redirect:/super-admin/solicitacoes";
    }
}
