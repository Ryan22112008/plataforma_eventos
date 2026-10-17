package sistema.eventos.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema.eventos.exception.NegocioException;
import sistema.eventos.model.Inscricao;
import sistema.eventos.model.Usuario;
import sistema.eventos.repository.SessaoRepository;
import sistema.eventos.service.PresencaService;
import sistema.eventos.service.InscricaoService;
import sistema.eventos.service.UsuarioService;
import sistema.eventos.service.CertificadoService; // ADICIONADO

import java.util.List;

@Controller
@RequestMapping("/participante")
public class ParticipanteController {

    private final InscricaoService inscricaoService;
    private final UsuarioService usuarioService;
    private final CertificadoService certificadoService; // ADICIONADO
    private final PresencaService presencaService;
    private final SessaoRepository sessaoRepository;

    public ParticipanteController(InscricaoService inscricaoService, UsuarioService usuarioService,
                                  CertificadoService certificadoService, PresencaService presencaService,
                                  SessaoRepository sessaoRepository) {
        this.inscricaoService = inscricaoService;
        this.usuarioService = usuarioService;
        this.certificadoService = certificadoService;
        this.presencaService = presencaService;
        this.sessaoRepository = sessaoRepository;
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
    public ResponseEntity<byte[]> visualizarCertificadoInline(@PathVariable("id") Long id,
                                                               @AuthenticationPrincipal UserDetails userDetails) {
        // Deixe as exceções subirem livremente. O GlobalExceptionHandler cuida delas.
        Inscricao inscricao = inscricaoService.buscarPorId(id);
        if (!inscricao.getParticipante().getEmail().equalsIgnoreCase(userDetails.getUsername())) {
            throw new org.springframework.security.access.AccessDeniedException("Este certificado não pertence à sua conta.");
        }
        byte[] pdfBytes = certificadoService.gerarCertificadoPdf(inscricao);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setCacheControl("private, no-store, max-age=0");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");

        // Força a abertura do PDF nativo dentro da aba do navegador
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("certificado-evento-" + id + ".pdf")
                .build());

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

    }

    @GetMapping("/presencas/checkin")
    public String confirmarPresenca(@RequestParam("token") String token, Model model) {
        var sessao = sessaoRepository.findByQrCodeToken(token)
                .orElseThrow(() -> new sistema.eventos.exception.NegocioException("QR Code inválido ou inexistente."));
        model.addAttribute("sessao", sessao);
        model.addAttribute("token", token);
        return "presenca-checkin";
    }

    @PostMapping("/presencas/checkin")
    public String registrarPresenca(@RequestParam("token") String token,
                                    @AuthenticationPrincipal UserDetails userDetails,
                                    RedirectAttributes attributes) {
        try {
            Usuario participante = usuarioService.buscarPorEmail(userDetails.getUsername());
            presencaService.registrarPresenca(participante.getId(), token);
            attributes.addFlashAttribute("mensagemSucesso", "Presença registrada para esta sessão.");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            attributes.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/participante/presencas/checkin?token=" + org.springframework.web.util.UriUtils.encodeQueryParam(token, java.nio.charset.StandardCharsets.UTF_8);
    }

}
