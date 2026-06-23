package sistema.eventos.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.InscricaoRepository;
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.TrabalhoService;
import sistema.eventos.service.UsuarioService;
import sistema.eventos.service.CertificadoService; // ADICIONADO

import java.util.List;

@Controller
@RequestMapping("/participante")
public class ParticipanteController {

    private final InscricaoService inscricaoService;
    private final InscricaoRepository inscricaoRepository;
    private final UsuarioService usuarioService;
    private final TrabalhoService trabalhoService;
    private final CertificadoService certificadoService; // ADICIONADO

    public ParticipanteController(InscricaoService inscricaoService, InscricaoRepository inscricaoRepository, UsuarioService usuarioService, TrabalhoService trabalhoService, CertificadoService certificadoService) {
        this.inscricaoService = inscricaoService;
        this.inscricaoRepository = inscricaoRepository;
        this.usuarioService = usuarioService;
        this.trabalhoService = trabalhoService;
        this.certificadoService = certificadoService;
    }

    @GetMapping("/meus-ingressos")
    public String meusIngressos(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Usuario logado = usuarioService.buscarPorEmail(userDetails.getUsername());
        model.addAttribute("inscricoes", inscricaoService.listarPorUsuario(logado));
        return "meus-ingressos";
    }

    @GetMapping("/inscricoes/{id}/submeter")
    public String telaSubmissao(@PathVariable Long id, Model model) {
        Inscricao inscricao = inscricaoService.buscarPorId(id);
        model.addAttribute("inscricao", inscricao);
        return "submeter-trabalho";
    }

    @PostMapping("/inscricoes/{id}/submeter")
    public String processarSubmissao(@PathVariable Long id,
                                     @RequestParam("titulo") String titulo,
                                     @RequestParam("file") MultipartFile file) {
        Inscricao inscricao = inscricaoService.buscarPorId(id);
        trabalhoService.salvarTrabalhoFisico(titulo, file, inscricao);
        return "redirect:/participante/meus-ingressos?sucesso=Seu+artigo+foi+submetido+com+sucesso!";
    }

    @GetMapping("/meus-certificados")
    public String meusCertificados(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Usuario participante = usuarioService.buscarPorEmail(userDetails.getUsername());
        List<Inscricao> certificadosDisponiveis = inscricaoService.buscarCertificadosDoUsuario(participante.getId());
        model.addAttribute("certificados", certificadosDisponiveis);
        return "meus-certificados";
    }

    // --- ADICIONADO: O motor de visualização do PDF na aba do navegador ---
    // Rota final: /participante/inscricoes/{id}/certificado
    // Rota final: /participante/inscricoes/{id}/certificado
    @GetMapping("/inscricoes/{id}/certificado")
    public ResponseEntity<byte[]> visualizarCertificadoInline(@PathVariable("id") Long id) {
        // Deixe as exceções subirem livremente. O GlobalExceptionHandler cuida delas.
        Inscricao inscricao = inscricaoService.buscarPorId(id);
        byte[] pdfBytes = certificadoService.gerarCertificadoPdf(inscricao);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);

        // Força a abertura do PDF nativo dentro da aba do navegador
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("certificado-evento-" + id + ".pdf")
                .build());

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

    }

    @GetMapping("/inscricoes/{id}/detalhes")
    public String verDetalhesInscricao(@PathVariable("id") Long inscricaoId, Authentication authentication, Model model) {
        try {
            if (authentication == null) {
                return "redirect:/login";
            }

            Inscricao inscricao = inscricaoRepository.findById(inscricaoId)
                    .orElseThrow(() -> new sistema.eventos.exception.NegocioException("Inscrição não encontrada."));

            // Barreira de segurança: O aluno só vê o recibo dele mesmo
            if (!inscricao.getParticipante().getEmail().equals(authentication.getName())) {
                return "redirect:/dashboard";
            }

            model.addAttribute("inscricao", inscricao);
            model.addAttribute("evento", inscricao.getEvento());

            return "detalhes-inscricao";
        } catch (Exception e) {
            return "redirect:/dashboard";
        }
    }

    // 🎯 CORRIGIDO TAMBÉM (Caso tenha colocado o check-in aqui):
    @GetMapping("/eventos/{id}/checkin")
    public String realizarCheckinAutonomo(@PathVariable("id") Long eventoId, Authentication authentication, RedirectAttributes attributes) {
        try {
            if (authentication == null) {
                return "redirect:/login";
            }

            String emailLogado = authentication.getName();
            Usuario aluno = usuarioService.buscarPorEmail(emailLogado);

            Inscricao inscricao = inscricaoRepository.findByParticipanteIdAndEventoIdComEvento(aluno.getId(), eventoId)
                    .orElseThrow(() -> new sistema.eventos.exception.NegocioException("Você não possui uma inscrição ativa para este evento."));

            if (inscricao.getEvento().isPresencaFinalizada()) {
                attributes.addFlashAttribute("mensagemErro", "A lista de presença deste evento já foi encerrada pelo professor.");
                return "redirect:/dashboard";
            }

            inscricao.setStatus(sistema.eventos.model.StatusInscricao.UTILIZADO);
            inscricaoRepository.save(inscricao);

            attributes.addFlashAttribute("mensagemSucesso", "Presença confirmada com sucesso!");
            return "redirect:/participante/meus-certificados"; // Redireciona para a sua tela oficial

        } catch (Exception e) {
            attributes.addFlashAttribute("mensagemErro", "Erro ao computar presença: " + e.getMessage());
            return "redirect:/dashboard";
        }
    }
}